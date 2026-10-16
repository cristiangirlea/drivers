import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { TempDir } from '../utils/files.js';
import {
  baseConnectionOptions,
  connectAsyncWithErrorBD,
  isRunningNewDriverWithBD,
  snowflake,
} from '../utils/index.js';
import { verifySimpleQuery } from '../utils/query.js';
import {
  acquireTotpPasscode,
  connectWithTotpRetry,
  destroyConnectionAfterTest,
  requireMfaCredentials,
} from './utils.js';

function mfaConnectionOptions(
  credentials: { user: string; password: string },
  extras: Record<string, unknown> = {},
) {
  return {
    ...baseConnectionOptions,
    username: credentials.user,
    password: credentials.password,
    authenticator: 'USERNAME_PASSWORD_MFA',
    ...mfaTokenCacheOptions(false),
    ...extras,
  };
}

function mfaTokenCacheOptions(enabled: boolean): Record<string, unknown> {
  if (isRunningNewDriverWithBD('BD#117')) {
    return { clientStoreTemporaryCredential: enabled };
  }
  // The old driver installs its default credential manager only when
  // clientStoreTemporaryCredential is set
  return enabled ? { clientRequestMFAToken: true, clientStoreTemporaryCredential: true } : {};
}

describe('USERNAME_PASSWORD_MFA authentication', () => {
  describe(
    'tests/definitions/shared/authentication/user_password_mfa.feature',
    { tags: ['requires_auth_test_container'], retry: 0 },
    () => {
      let tokenCacheDir: TempDir;

      // sf_core resolves the token cache directory once per process, so a
      // per-test directory would leave later tests writing to a deleted path.
      beforeAll(() => {
        tokenCacheDir = new TempDir();
        vi.stubEnv('SF_TEMPORARY_CREDENTIAL_CACHE_DIR', tokenCacheDir.path);
      });

      afterAll(() => {
        vi.unstubAllEnvs();
        tokenCacheDir.cleanup();
      });

      // Duo push requires interactive device approval, so this runs manually only.
      it.skip('should authenticate using username password and DUO push', async () => {
        // Given Authentication is set to username_password_mfa and user, password are provided and DUO push is enabled
        const credentials = requireMfaCredentials();
        const connection = snowflake.createConnection(mfaConnectionOptions(credentials));

        // When Trying to Connect
        await connectAsyncWithErrorBD(connection);
        destroyConnectionAfterTest(connection);

        // Then Login is successful and simple query can be executed
        await verifySimpleQuery(connection);
      });

      it('should authenticate using username password and TOTP passcode', async (context) => {
        // Given Authentication is set to username_password_mfa and user, password and passcode are provided
        const credentials = requireMfaCredentials();

        // When Trying to Connect
        const connection = await connectWithTotpRetry(
          mfaConnectionOptions(credentials),
          credentials.totpSeed,
          { skip: context.skip },
        );
        destroyConnectionAfterTest(connection);

        // Then Login is successful and simple query can be executed
        await verifySimpleQuery(connection);
      });

      it('should authenticate using username password with appended TOTP passcode', async (context) => {
        // Given Authentication is set to username_password_mfa and user, password with appended passcode are provided and passcodeInPassword is set
        const credentials = requireMfaCredentials();

        // When Trying to Connect
        const connection = await connectWithTotpRetry(
          mfaConnectionOptions(credentials),
          credentials.totpSeed,
          { passcodeInPassword: true, skip: context.skip },
        );
        destroyConnectionAfterTest(connection);

        // Then Login is successful and simple query can be executed
        await verifySimpleQuery(connection);
      });

      // Bad-secret tests cause pipeline flakiness by blocking the shared MFA test account.
      it.skip('should fail MFA authentication when wrong password is provided', async (context) => {
        // Given Authentication is set to username_password_mfa and user is provided but password is skipped or invalid
        const credentials = requireMfaCredentials();
        const passcode = await acquireTotpPasscode(credentials.totpSeed, context.skip);
        const connection = snowflake.createConnection(
          mfaConnectionOptions(credentials, { password: 'wrong_password', passcode }),
        );

        // When Trying to Connect
        const connectionAttempt = connectAsyncWithErrorBD(connection);

        // Then There is error returned
        await expect(connectionAttempt).rejects.toMatchObject({ code: '390100' });
      });

      it('should reuse cached MFA token without passcode', async (context) => {
        // Given Authentication is set to username_password_mfa and MFA token has been cached from a previous connection
        const credentials = requireMfaCredentials();
        const cacheOptions = mfaTokenCacheOptions(true);
        const first = await connectWithTotpRetry(
          mfaConnectionOptions(credentials, cacheOptions),
          credentials.totpSeed,
          { skip: context.skip },
        );
        destroyConnectionAfterTest(first);
        await verifySimpleQuery(first);

        // When Trying to Connect without passcode
        const second = snowflake.createConnection(mfaConnectionOptions(credentials, cacheOptions));
        await connectAsyncWithErrorBD(second);
        destroyConnectionAfterTest(second);

        // Then Login is successful and simple query can be executed
        await verifySimpleQuery(second);
      });
    },
  );
});
