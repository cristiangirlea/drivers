import { Buffer } from 'node:buffer';
import { describe, expect, it, onTestFinished } from 'vitest';
import type { Connection, ConnectionOptions } from '../../types/sdk-types.js';
import getTestParameter, { getTestParametersFromSameSource } from '../utils/getTestParameter.js';
import {
  baseConnectionOptions,
  connectAsyncWithErrorBD,
  destroyConnectionAsync,
  isRunningNewDriverWithBD,
  snowflake,
} from '../utils/index.js';
import { verifySimpleQuery } from '../utils/query.js';
import {
  cleanBrowserProcesses,
  connectWithBrowserAutomation,
  destroyConnectionAfterTest,
  expectConnectionNotUp,
  provideBrowserCredentials,
  NEVER_ESTABLISHED_QUERY,
  requireOktaCredentials,
  TERMINATED_QUERY,
} from './utils.js';

function formatSingleIssueConfigurationValidationMessage(
  parameter: string,
  detail: string,
): string {
  return `Configuration error: Configuration validation failed (1 issue(s)): ${parameter}: ${detail}`;
}

async function createLiveOauthConnection(
  options: ConnectionOptions,
  shouldCleanupAfterTest = true,
): Promise<Connection> {
  const connection = snowflake.createConnection({ ...options });
  await connection.connectAsync();
  if (shouldCleanupAfterTest) {
    destroyConnectionAfterTest(connection);
  }
  return connection;
}

async function mintOauthAccessToken(user: string, password: string): Promise<string> {
  const clientId = getTestParameter('SNOWFLAKE_TEST_OKTA_OAUTH_CLIENT_ID', true);
  const clientSecret = getTestParameter('SNOWFLAKE_TEST_OKTA_OAUTH_CLIENT_SECRET', true);
  const role = getTestParameter('SNOWFLAKE_TEST_ROLE', true);
  const tokenUrl = getTestParameter('SNOWFLAKE_TEST_OKTA_OAUTH_TOKEN_URL', true);
  const response = await fetch(tokenUrl, {
    method: 'POST',
    headers: {
      Authorization: `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`,
      'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
    },
    body: new URLSearchParams({
      username: user,
      password,
      grant_type: 'password',
      scope: `session:role:${role.toLowerCase()}`,
    }),
  });

  if (!response.ok) {
    // The IdP reports the reason (invalid_grant, invalid_scope, ...) only in the
    // body; the status alone does not distinguish them.
    throw new Error(
      `OAuth token request failed with HTTP ${response.status}: ${await response.text()}`,
    );
  }

  const payload = (await response.json()) as { access_token?: unknown };
  if (typeof payload.access_token !== 'string' || payload.access_token.length === 0) {
    throw new Error('OAuth token response did not contain an access token');
  }
  return payload.access_token;
}

type AuthorizationCodeCredentials = {
  user: string;
  password: string;
  clientId: string;
  clientSecret: string;
  redirectUri: string;
  totpSeed: string;
};

function requireAuthorizationCodeCredentials(): AuthorizationCodeCredentials {
  const loginKeys = [
    'SNOWFLAKE_TEST_OAUTH_SNOWFLAKE_USER',
    'SNOWFLAKE_TEST_OAUTH_SNOWFLAKE_PASSWORD',
    'SNOWFLAKE_TEST_OAUTH_SNOWFLAKE_MFA_SEED',
  ] as const;
  const loginCredentials = getTestParametersFromSameSource(loginKeys);
  if (!loginCredentials) {
    throw new Error(`No parameters source defines ${loginKeys.join(', ')}`);
  }
  const {
    SNOWFLAKE_TEST_OAUTH_SNOWFLAKE_USER: user,
    SNOWFLAKE_TEST_OAUTH_SNOWFLAKE_PASSWORD: password,
    SNOWFLAKE_TEST_OAUTH_SNOWFLAKE_MFA_SEED: totpSeed,
  } = loginCredentials;
  const clientKeys = [
    'SNOWFLAKE_TEST_OAUTH_SNOWFLAKE_CLIENT_ID',
    'SNOWFLAKE_TEST_OAUTH_SNOWFLAKE_CLIENT_SECRET',
  ] as const;
  const clientCredentials = getTestParametersFromSameSource(clientKeys);
  if (!clientCredentials) {
    throw new Error(`No parameters source defines ${clientKeys.join(', ')}`);
  }
  const {
    SNOWFLAKE_TEST_OAUTH_SNOWFLAKE_CLIENT_ID: clientId,
    SNOWFLAKE_TEST_OAUTH_SNOWFLAKE_CLIENT_SECRET: clientSecret,
  } = clientCredentials;
  return {
    user,
    password,
    totpSeed,
    clientId,
    clientSecret,
    redirectUri: getTestParameter('SNOWFLAKE_TEST_OAUTH_SNOWFLAKE_REDIRECT_URI', true),
  };
}

function createAuthorizationCodeOptions(clientStoreTemporaryCredential: boolean): {
  options: ConnectionOptions;
  credentials: AuthorizationCodeCredentials;
} {
  const credentials = requireAuthorizationCodeCredentials();
  return {
    options: {
      ...baseConnectionOptions,
      username: credentials.user,
      authenticator: 'OAUTH_AUTHORIZATION_CODE',
      oauthClientId: credentials.clientId,
      oauthClientSecret: credentials.clientSecret,
      oauthRedirectUri: credentials.redirectUri,
      clientStoreTemporaryCredential,
      browserActionTimeout: 120000,
    } as ConnectionOptions,
    credentials,
  };
}

function connectWithOauthAuthorizationCode(
  connect: () => Promise<unknown>,
  credentials: Pick<AuthorizationCodeCredentials, 'user' | 'password' | 'totpSeed'>,
): Promise<void> {
  return connectWithBrowserAutomation(connect, () =>
    provideBrowserCredentials(
      'internalOauthSnowflakeSuccess',
      credentials.user,
      credentials.password,
      credentials.totpSeed,
    ),
  );
}

describe('tests/definitions/shared/authentication/oauth.feature', () => {
  describe('OAuth wrapper validation', () => {
    const FAKE_HOST_CONNECTION_OPTIONS = {
      account: 'test-account',
      host: '127.0.0.1',
      username: 'test-user',
      browserActionTimeout: 2000,
      retryTimeout: 300,
      // TODO(SNOW-3996212): BD#90 — the old driver retries unreachable-host logins
      // (default 7) unless this count is 0. The new driver does not accept the option
      // and should also avoid those retries.
      ...(isRunningNewDriverWithBD('BD#90') ? {} : { sfRetryMaxLoginRetries: 0 }),
    };

    it('should forward AUTHENTICATOR=OAUTH with TOKEN to core', async () => {
      // Given Authentication is set to legacy OAUTH with a pre-acquired access token
      const options = {
        ...FAKE_HOST_CONNECTION_OPTIONS,
        authenticator: 'OAUTH',
        token: 'fake.jwt.token',
      };

      // When Trying to Connect
      const connection = snowflake.createConnection({ ...options });

      // Then The wrapper forwards the token to sf_core without raising a missing-parameter error for it
      await expect(connectAsyncWithErrorBD(connection)).rejects.toMatchObject({
        name: 'NetworkError',
        code: 401001,
        message: isRunningNewDriverWithBD('BD#97')
          ? 'Network error. Could not reach Snowflake.'
          : 'connect ECONNREFUSED 127.0.0.1:443',
      });
    });

    it('should fail AUTHENTICATOR=OAUTH when TOKEN is missing', async () => {
      // Given Authentication is set to legacy OAUTH without a TOKEN
      const options = {
        ...FAKE_HOST_CONNECTION_OPTIONS,
        authenticator: 'OAUTH',
      };

      // When Trying to Connect
      const connection = snowflake.createConnection({ ...options });

      // Then Connection fails with a missing-parameter error citing token
      await expect(connectAsyncWithErrorBD(connection)).rejects.toMatchObject(
        isRunningNewDriverWithBD('BD#100')
          ? {
              name: 'InvalidParameterError',
              code: undefined,
              message: formatSingleIssueConfigurationValidationMessage(
                'token',
                "Missing required parameter 'token' (or 'token_file_path') for OAuth authentication",
              ),
            }
          : {
              name: 'NetworkError',
              code: 401001,
              message: 'connect ECONNREFUSED 127.0.0.1:443',
            },
      );
    });

    it('should accept lowercase oauth authenticator value', async () => {
      // Given Authentication is set to lowercase oauth with a TOKEN
      const options = {
        ...FAKE_HOST_CONNECTION_OPTIONS,
        authenticator: 'oauth',
        token: 'fake.jwt.token',
      };

      // When Trying to Connect
      const connection = snowflake.createConnection({ ...options });

      // Then The wrapper does not reject the AUTHENTICATOR value as unknown
      await expect(connectAsyncWithErrorBD(connection)).rejects.toMatchObject({
        name: 'NetworkError',
        code: 401001,
        message: isRunningNewDriverWithBD('BD#97')
          ? 'Network error. Could not reach Snowflake.'
          : 'connect ECONNREFUSED 127.0.0.1:443',
      });
    });

    it('should fail when AUTHENTICATOR is an unknown OAuth-like value', async () => {
      // Given Authentication is set to a typo of an OAuth flow name
      const options = {
        ...FAKE_HOST_CONNECTION_OPTIONS,
        authenticator: 'OAUTH_AUTHORIZATION_TYPO',
      };

      // When Trying to Connect
      const connection = snowflake.createConnection({ ...options });

      // Then Connection fails with an authenticator-related error
      await expect(connectAsyncWithErrorBD(connection)).rejects.toMatchObject(
        isRunningNewDriverWithBD('BD#99')
          ? {
              name: 'InvalidParameterError',
              code: undefined,
              message: formatSingleIssueConfigurationValidationMessage(
                'authenticator',
                "Invalid authenticator 'OAUTH_AUTHORIZATION_TYPO'. Allowed values are snowflake, snowflake_jwt, snowflake_password, programmatic_access_token, pat_with_external_session, username_password_mfa, externalbrowser, oauth, oauth_client_credentials, oauth_authorization_code, workload_identity or an https:// URL for native Okta SSO (case-insensitive)",
              ),
            }
          : {
              name: 'NetworkError',
              code: 401001,
              message: 'connect ECONNREFUSED 127.0.0.1:443',
            },
      );
    });
  });

  describe('Live OAuth authentication', { tags: ['requires_auth_test_container'] }, () => {
    it('oauth should authenticate with pre acquired access token', async () => {
      // Given Authentication is set to legacy OAUTH and a pre-acquired OAuth access token is supplied via `token=`
      const { user, password } = requireOktaCredentials();
      const token = await mintOauthAccessToken(user, password);

      // When Trying to Connect
      const connection = await createLiveOauthConnection({
        ...baseConnectionOptions,
        username: user,
        authenticator: 'OAUTH',
        token,
      });

      // Then Login is successful and a simple query can be executed
      await verifySimpleQuery(connection);
    });

    // Python, JDBC, and ODBC skip this scenario so a junk TOKEN= login does not lock the
    // shared account. Core ignores every OAuth e2e test behind auth_oauth_e2e.
    it.skip('oauth should fail legacy authentication with invalid token', async () => {
      // Given Authentication is set to legacy OAUTH and an invalid OAuth access token is supplied
      const { user } = requireOktaCredentials();
      const connection = snowflake.createConnection({
        ...baseConnectionOptions,
        username: user,
        authenticator: 'OAUTH',
        token: 'invalid_oauth_token_12345',
      });

      // When Trying to Connect
      // Then Connection fails with an authentication / login error
      await expect(connectAsyncWithErrorBD(connection)).rejects.toMatchObject({
        message: expect.stringMatching(/invalid oauth access token/i),
      });

      // And A subsequent query fails because the connection is not usable
      await expectConnectionNotUp(connection);
    });

    it('oauth should authenticate using authorization code flow', async () => {
      // Given Authentication is set to OAUTH_AUTHORIZATION_CODE with a valid client id / secret
      const { options, credentials } = createAuthorizationCodeOptions(false);
      const connection = snowflake.createConnection({ ...options });
      await cleanBrowserProcesses();

      // When Trying to Connect (this will spawn the local-loopback HTTP listener and `xdg-open`/`open`/`ShellExecute` the IdP login URL unless a previously cached access token short-circuits the leg)
      await connectWithOauthAuthorizationCode(() => connection.connectAsync(), credentials);
      destroyConnectionAfterTest(connection);

      // Then Login is successful and a simple query can be executed
      await verifySimpleQuery(connection);
    });

    it('oauth should reuse cached access token without browser interaction', async () => {
      const { options, credentials } = createAuthorizationCodeOptions(true);
      const browserLoginConnection = snowflake.createConnection({ ...options });
      await cleanBrowserProcesses();
      await connectWithOauthAuthorizationCode(
        () => browserLoginConnection.connectAsync(),
        credentials,
      );
      onTestFinished(async () => {
        await destroyConnectionAsync(browserLoginConnection);
      });
      await verifySimpleQuery(browserLoginConnection);

      // Given Authentication is set to OAUTH_AUTHORIZATION_CODE with client_store_temporary_credential=true and a token has been cached from a previous browser authentication

      const cachedTokenConnection = snowflake.createConnection({
        ...({
          ...options,
          openExternalBrowserCallback: () => {
            throw new Error('Browser should not be launched');
          },
        } as ConnectionOptions),
      });
      // When Trying to Connect without browser interaction
      await cachedTokenConnection.connectAsync();
      destroyConnectionAfterTest(cachedTokenConnection);

      // Then Login is successful and a simple query can be executed
      await verifySimpleQuery(cachedTokenConnection);
    });

    describe.skip('should avoid bad-secret account lockouts', () => {
      it('oauth should fail authorization code flow with bad client secret', async () => {
        // Given Authentication is set to OAUTH_AUTHORIZATION_CODE with a valid client id but a deliberately invalid client secret
        const { options, credentials } = createAuthorizationCodeOptions(false);
        const connection = snowflake.createConnection({
          ...({
            ...options,
            oauthClientSecret: 'invalid_client_secret_12345',
          } as ConnectionOptions),
        });
        await cleanBrowserProcesses();

        // When Trying to Connect
        const connecting = connectWithOauthAuthorizationCode(
          () => connection.connectAsync(),
          credentials,
        );

        // Then Connection fails with an authentication / login error
        await expect(connecting).rejects.toMatchObject({
          message: expect.stringContaining('invalid_client'),
        });
      });
    });

    it('oauth should authenticate using lowercase oauth authenticator', async () => {
      // Given Authentication is set to lowercase oauth and a valid pre-acquired OAuth access token is supplied via TOKEN
      const { user, password } = requireOktaCredentials();

      // When Trying to Connect
      const connection = await createLiveOauthConnection({
        ...baseConnectionOptions,
        username: user,
        authenticator: 'oauth',
        token: await mintOauthAccessToken(user, password),
      });

      // Then Login is successful and a simple query can be executed
      await verifySimpleQuery(connection);
    });

    it('should authenticate with pre acquired access token without user', async () => {
      // Given Authentication is set to legacy OAUTH and a pre-acquired OAuth access token is supplied via `token=` and user is omitted
      const { user, password } = requireOktaCredentials();
      // SNOWFLAKE_TEST_USER must not ride along: the token identifies the user.
      const { username, ...connectionOptionsWithoutUser } = baseConnectionOptions;
      void username;

      // When Trying to Connect without user
      const connection = await createLiveOauthConnection({
        ...connectionOptionsWithoutUser,
        authenticator: 'OAUTH',
        token: await mintOauthAccessToken(user, password),
      });

      // Then Login is successful and a simple query can be executed
      await verifySimpleQuery(connection);
    });

    it('should reject a mismatched username for a pre-acquired token', async () => {
      // Given Authentication is set to a pre-acquired token with a username that is not the user tied to the access token
      const { user, password } = requireOktaCredentials();
      const connection = snowflake.createConnection({
        ...baseConnectionOptions,
        username: 'itsnotanaccount.com',
        authenticator: 'OAUTH',
        token: await mintOauthAccessToken(user, password),
      });

      // When Trying to Connect
      const connecting = connectAsyncWithErrorBD(connection);

      // Then Connection fails with "The user you were trying to authenticate as differs from the user tied to the access token."
      await expect(connecting).rejects.toMatchObject(
        isRunningNewDriverWithBD('BD#98')
          ? {
              name: 'OperationFailedError',
              code: '390309',
              message: expect.stringMatching(
                /^Failed to login: Login error: The user you were trying to authenticate as differs from the user tied to the access token\. .+, code: 390309$/,
              ),
            }
          : {
              name: 'OperationFailedError',
              code: '390309',
              message: expect.stringMatching(
                /^The user you were trying to authenticate as differs from the user tied to the access token\. .+$/,
              ),
            },
      );
      await expectConnectionNotUp(connection);
    });
  });
});

it(
  'should time out authorization code when browser credentials are not provided',
  { tags: ['requires_auth_test_container'] },
  async () => {
    const { options } = createAuthorizationCodeOptions(false);
    const connection = snowflake.createConnection({
      ...({
        ...options,
        browserActionTimeout: 2000,
      } as ConnectionOptions),
    });
    onTestFinished(cleanBrowserProcesses);
    await cleanBrowserProcesses();
    await expect(connectAsyncWithErrorBD(connection)).rejects.toMatchObject(
      isRunningNewDriverWithBD('BD#95')
        ? { message: expect.stringContaining('OAuth browser authorization timed out') }
        : { message: expect.stringContaining('Browser action timed out after 2000 ms.') },
    );
    await expectConnectionNotUp(
      connection,
      isRunningNewDriverWithBD('BD#61') ? TERMINATED_QUERY : NEVER_ESTABLISHED_QUERY,
    );
  },
);

describe('sfRetryMaxLoginRetries', () => {
  it('should reject sfRetryMaxLoginRetries as an unknown option', () => {
    const options = {
      account: 'test-account',
      username: 'test-user',
      authenticator: 'OAUTH',
      token: 'fake.jwt.token',
      sfRetryMaxLoginRetries: 0,
    };
    if (isRunningNewDriverWithBD('BD#90')) {
      expect(() => snowflake.createConnection({ ...options })).toThrow(
        'Unknown connection option: sfRetryMaxLoginRetries',
      );
    } else {
      snowflake.createConnection({ ...options });
    }
  });
});
