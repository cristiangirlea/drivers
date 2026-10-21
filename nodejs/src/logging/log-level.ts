import { invalidParameterError } from '../error.js';

export type LogLevel = 'ERROR' | 'WARN' | 'INFO' | 'DEBUG' | 'TRACE' | 'OFF';

/** sf_core folds TRACE into DEBUG, so both map to the same value. */
export const LOG_LEVEL_TO_CORE_VALUE: Record<LogLevel, number> = {
  OFF: -1,
  ERROR: 0,
  WARN: 1,
  INFO: 2,
  DEBUG: 3,
  TRACE: 3,
};

/** Accepts any letter case, because the old driver did. */
export function parseLogLevel(value?: string): LogLevel | undefined {
  if (value === undefined) {
    return undefined;
  }
  const level = value.toUpperCase();
  if (!(level in LOG_LEVEL_TO_CORE_VALUE)) {
    throw invalidParameterError(`Invalid logLevel: ${value}`);
  }
  return level as LogLevel;
}
