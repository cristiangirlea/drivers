import { XMLParser, XMLValidator, X2jOptions } from 'fast-xml-parser';
import ErrorCode from './constants/ErrorCode.js';
import ErrorMessage from './constants/ErrorMessage.js';
import { coreSetCustomCredentialManager } from './core/index.js';
import { invalidParameterError } from './error.js';
import { defaultLogger } from './logging/default-logger.js';
import { parseLogLevel, type LogLevel } from './logging/log-level.js';
import { logger, logRouter, type SnowflakeLogger } from './logging/logger.js';

export type CustomParser = (rawColumnValue: string) => any;
export type XMlParserConfigOption = X2jOptions;

/** Token store; methods may return a value or a Promise that settles within 30 seconds. */
export interface CustomCredentialManager {
  read(key: string): string | null | undefined | Promise<string | null | undefined>;
  write(key: string, token: string): unknown | Promise<unknown>;
  remove(key: string): unknown | Promise<unknown>;
}

export interface ConfigureOptions {
  /**
   * Log verbosity for driver and core records, in any letter case. `TRACE` logs the same
   * records as `DEBUG`, and `OFF` logs nothing.
   *
   * @default 'INFO'
   */
  logLevel?: LogLevel;

  /**
   * Path of the JSON-line log file. Missing parent directories are created, and the file is
   * opened by `configure` so an unusable path fails there. `STDOUT` (any letter case) writes
   * to stdout only.
   *
   * @default 'snowflake.log'
   */
  logFilePath?: string;

  /**
   * Whether log lines also go to stdout when they are written to a file.
   *
   * @default true
   */
  additionalLogToConsole?: boolean | null;

  /**
   * Receives the level-filtered, formatted messages instead of the built-in file and console
   * output. Setting it back to `undefined` restores the built-in output.
   */
  customLogger?: SnowflakeLogger;

  /**
   * Custom parser for JSON data in VARIANT, OBJECT, and ARRAY columns.
   *
   * By default the driver parses values with `JSON.parse()`. If that fails (e.g. the
   * value contains non-standard tokens like `undefined`, `NaN`, or `Infinity` that
   * Snowflake's VARIANT type allows), it falls back to eval-based parsing, which is
   * slower and logs a warning.
   *
   * To avoid the fallback, set the `STRICT_JSON_OUTPUT` session parameter to `TRUE` so
   * Snowflake normalizes non-standard values into valid JSON before sending them.
   *
   * @see https://docs.snowflake.com/en/developer-guide/node-js/nodejs-driver-consume
   * @see https://docs.snowflake.com/en/sql-reference/parameters#strict-json-output
   */
  jsonColumnVariantParser?: CustomParser;

  /**
   * Custom parser for XML data in VARIANT columns.
   *
   * The driver always attempts JSON parsing first for every VARIANT value. Only when
   * JSON parsing fails does it try this XML parser, so XML values always incur the
   * overhead of a failed JSON parse attempt before being handled.
   *
   * The built-in parser uses `fast-xml-parser` and ignores XML attributes by default.
   * Use `xmlParserConfig` to customize attribute handling.
   *
   * @see https://docs.snowflake.com/en/developer-guide/node-js/nodejs-driver-consume
   */
  xmlColumnVariantParser?: CustomParser;

  /**
   * Configuration passed through to `fast-xml-parser`, the library backing the
   * built-in `xmlColumnVariantParser`.
   *
   * These options are forwarded directly to the `XMLParser` constructor, so they
   * control how XML in VARIANT columns is parsed (e.g. attribute handling, tag value
   * processing). See `fast-xml-parser`'s `X2jOptions` for the full set of options.
   *
   * Has no effect if you supply your own `xmlColumnVariantParser`.
   */
  xmlParserConfig?: XMlParserConfigOption;

  /**
   * Whether the driver reads proxy settings from the `HTTP_PROXY`, `HTTPS_PROXY`, and `NO_PROXY`
   * environment variables when a connection does not configure an explicit proxy. An explicit
   * `proxyHost`/`proxyPort` always takes precedence over the environment.
   *
   * @default true
   */
  useEnvProxy?: boolean;

  /**
   * HTTP request timeout for CRL download, in milliseconds. Applies to connections created after
   * the call. The value is rounded down to whole seconds; a positive value under 1000 becomes 1
   * second.
   *
   * @default 10000
   */
  crlDownloadTimeout?: number;

  /**
   * Maximum allowed size of a CRL download response, in bytes. Applies to connections created
   * after the call.
   *
   * @default 20971520 (20 MB)
   */
  crlDownloadMaxSize?: number;

  /**
   * Time after which cached CRL entries are invalidated, in milliseconds. Applies to connections
   * created after the call. The value is rounded down to whole seconds.
   *
   * @default 86400000 (24 hours)
   */
  crlCacheValidityTime?: number;

  /**
   * Directory for the on-disk CRL cache. Applies to connections created after the call.
   *
   * @default process.env.SNOWFLAKE_CRL_ON_DISK_CACHE_DIR, otherwise the driver's default cache
   * directory
   */
  crlCacheDir?: string;

  /**
   * Token store that replaces the OS keyring for cached credentials.
   *
   * Once registered, it is used by default for MFA, external browser, and OAuth
   * Authorization Code logins, because `clientStoreTemporaryCredential` defaults to `true`.
   * A connection that sets `clientStoreTemporaryCredential: false` never calls it.
   *
   * It receives ID tokens, MFA tokens, OAuth access and refresh tokens, and DPoP private keys.
   */
  customCredentialManager?: CustomCredentialManager;
}

const LOGGING_OPTIONS: (keyof ConfigureOptions)[] = [
  'logLevel',
  'logFilePath',
  'additionalLogToConsole',
  'customLogger',
];

type ResolvedConfig = Required<
  Omit<ConfigureOptions, 'customCredentialManager' | 'crlCacheDir' | 'customLogger'>
> &
  Pick<ConfigureOptions, 'crlCacheDir' | 'customLogger'>;

const buildDefaults = (): ResolvedConfig => ({
  logLevel: 'INFO',
  logFilePath: 'snowflake.log',
  additionalLogToConsole: true,
  customLogger: undefined,
  jsonColumnVariantParser: (
    rawColumnValue: string,
    // NOTE: We're intentionally not documenting the context argument in CustomParser to match old driver typing
    context?: {
      onNonJsonCompliantVariant: () => void;
    },
  ) => {
    try {
      return JSON.parse(rawColumnValue);
    } catch {
      // NOTE:
      // JSON.parse (RFC 8259) throws on values such as undefined, NaN, and Infinity,
      // but Snowflake can return them when STRICT_JSON_OUTPUT is FALSE (default).
      // This fallback evaluates only VARIANT output from the trusted Snowflake service,
      // whose encoder does not emit executable JavaScript.
      //
      // https://docs.snowflake.com/en/sql-reference/parameters#strict_json_output
      const result = new Function(`return (${rawColumnValue});`)();
      context?.onNonJsonCompliantVariant();
      return result;
    }
  },
  xmlColumnVariantParser: (rawColumnValue: string) => {
    const validateResult = XMLValidator.validate(rawColumnValue);
    if (validateResult === true) {
      return new XMLParser(GlobalConfig.xmlParserConfig).parse(rawColumnValue);
    } else {
      throw new Error(validateResult.err.msg);
    }
  },
  xmlParserConfig: {},
  useEnvProxy: true,
  crlDownloadTimeout: 10000,
  crlDownloadMaxSize: 20 * 1024 * 1024,
  crlCacheValidityTime: 86400000,
  crlCacheDir: process.env.SNOWFLAKE_CRL_ON_DISK_CACHE_DIR,
});

export const GlobalConfig: ResolvedConfig = {
  ...buildDefaults(),
};

export function updateGlobalConfig({ customCredentialManager, ...options }: ConfigureOptions) {
  if (customCredentialManager != null) {
    setCustomCredentialManager(customCredentialManager);
  }
  const logLevel = parseLogLevel(options.logLevel);
  Object.assign(GlobalConfig, options, logLevel && { logLevel });

  if (LOGGING_OPTIONS.some((key) => key in options)) {
    applyLoggingOptions();
  }
}

function applyLoggingOptions() {
  logRouter.syncLevel();
  if (!GlobalConfig.customLogger && GlobalConfig.logLevel !== 'OFF') {
    defaultLogger.openFile();
  }
  logger.info(
    'Configuring logger with level: %s, filePath: %s, additionalLogToConsole: %s, customLogger: %s',
    GlobalConfig.logLevel,
    GlobalConfig.logFilePath,
    GlobalConfig.additionalLogToConsole,
    GlobalConfig.customLogger !== undefined,
  );
}

// TODO:
// - entire thing can live in nodejs_bridge including CustomCredentialManager
// - remove validation and trust TypeScript
function setCustomCredentialManager(manager: CustomCredentialManager) {
  const isValid =
    typeof manager === 'object' &&
    (['read', 'write', 'remove'] as const).every(
      (method) => Object.hasOwn(manager, method) && typeof manager[method] === 'function',
    );
  if (!isValid) {
    const code = ErrorCode.ERR_GLOBAL_CONFIGURE_INVALID_CUSTOM_CREDENTIAL_MANAGER;
    throw invalidParameterError(ErrorMessage[code], code);
  }
  coreSetCustomCredentialManager({
    read: async (key) => manager.read(key),
    write: async (key, token) => {
      await manager.write(key, token);
    },
    remove: async (key) => {
      await manager.remove(key);
    },
  });
}

export const resetGlobalConfig = () => {
  Object.assign(GlobalConfig, buildDefaults());
  coreSetCustomCredentialManager(null);
  defaultLogger.close();
  logRouter.syncLevel();
};
