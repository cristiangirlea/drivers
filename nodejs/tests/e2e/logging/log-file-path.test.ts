import { execFile } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { describe, expect, it } from 'vitest';
import { createTempDir } from '../utils/fixtures.js';
import { IS_RUNNING_FOR_OLD_DRIVER } from '../utils/index.js';

const NODEJS_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');

// An unopenable log file ends the process, so the driver runs in a child process.
const WORKER_CODE = `
const { default: snowflake } = await import(process.env.SF_DRIVER_MODULE);
snowflake.configure({
  logLevel: 'INFO',
  logFilePath: process.env.SF_LOG_FILE_PATH,
  additionalLogToConsole: false,
});
// The old driver opens the log file asynchronously; this pause gives that failure time to
// end the process before the worker reports that it is still running.
await new Promise((resolve) => setTimeout(resolve, 500));
console.log('still running');
`;

function runWorker(logFilePath: string): Promise<{ stdout: string; stderr: string }> {
  return promisify(execFile)(process.execPath, ['--input-type=module', '-e', WORKER_CODE], {
    cwd: NODEJS_ROOT,
    env: {
      ...process.env,
      SF_DRIVER_MODULE: IS_RUNNING_FOR_OLD_DRIVER ? 'snowflake-sdk-old' : 'snowflake-sdk',
      SF_LOG_FILE_PATH: logFilePath,
    },
    timeout: 30_000,
  });
}

describe('logFilePath', () => {
  it('should end the process when the log file cannot be opened', async () => {
    const regularFile = path.join(createTempDir().path, 'not-a-directory');
    fs.writeFileSync(regularFile, '');
    const logFilePath = path.join(regularFile, 'snowflake.log');

    await expect(runWorker(logFilePath)).rejects.toMatchObject({
      stdout: '',
      stderr: expect.stringMatching(/ENOTDIR|EEXIST/),
    });
  });
});
