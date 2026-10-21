import { execFile } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { RESOURCE_NAMES } from './fixtures/process-exit-scenario.cjs';
import { IS_RUNNING_FOR_OLD_DRIVER } from './utils/index.js';
import {
  authenticatorRequestSuccess,
  heartbeatSuccess,
  loginSuccess,
  logoutSuccess,
  type StubMapping,
  WiremockServer,
} from './utils/wiremock/index.js';

const SCENARIO_SCRIPT = fileURLToPath(
  new URL('./fixtures/process-exit-scenario.cjs', import.meta.url),
);
const EXIT_TIMEOUT_MS = 5_000;

// Keys match the lifecycles in `fixtures/process-exit-scenario.cjs`.
const LIFECYCLE_STUBS: Record<string, StubMapping[]> = {
  'never connected': [],
  'connected then destroyed': [
    authenticatorRequestSuccess(),
    loginSuccess(),
    heartbeatSuccess(),
    logoutSuccess(),
  ],
  'failed to connect': [],
};

/**
 * Runs the scenario script in a fresh Node process and resolves once that process exits
 * on its own. The script never calls `process.exit`, so a handle the driver leaves
 * referenced (for example a strong napi threadsafe function) keeps it alive past the
 * timeout.
 *
 * A separate process is the only reliable detector for this class of leak:
 * - Vitest tears down its own workers, so a referenced handle never blocks the run the
 *   way it blocks a plain `node` script or Mocha without `--exit`.
 * - `process.getActiveResourcesInfo()`, and the Vitest `hanging-process` reporter built
 *   on the same data, list only handles Node itself owns. A strong threadsafe function
 *   created by the native addon is not listed: the list is empty while the process hangs.
 * - Vitest `detectAsyncLeaks` tracks async resources through `async_hooks`, not whether
 *   they keep the loop alive. It reports `napi_rs_threadsafe_function` for weak and
 *   strong functions alike, reports dozens of unrelated driver resources, and does not
 *   fail the run (as of Vitest 4.1).
 */
function waitForProcessToExit(args: string[]): Promise<void> {
  return new Promise((resolve, reject) => {
    execFile(
      process.execPath,
      [SCENARIO_SCRIPT, ...args],
      { timeout: EXIT_TIMEOUT_MS },
      (error, stdout, stderr) => {
        if (!error) return resolve();
        const reason = error.killed
          ? `process did not exit within ${EXIT_TIMEOUT_MS}ms`
          : `process exited with ${error.code}`;
        reject(new Error(`${reason}\nstdout:\n${stdout}\nstderr:\n${stderr}`));
      },
    );
  });
}

describe.skipIf(IS_RUNNING_FOR_OLD_DRIVER)('process exit', () => {
  let wiremock: WiremockServer;

  beforeAll(async () => {
    wiremock = await WiremockServer.spawn();
  });

  afterAll(async () => {
    await wiremock.destroy();
  });

  beforeEach(async () => {
    await wiremock.reset();
  });

  describe.each(RESOURCE_NAMES)('with %s', (resource) => {
    it.each(Object.entries(LIFECYCLE_STUBS))(
      'should let the process exit when the connection is %s',
      async (lifecycle, stubs) => {
        await wiremock.stub(stubs);

        await waitForProcessToExit([
          resource,
          lifecycle,
          JSON.stringify(wiremock.connectionOptions),
        ]);
      },
    );
  });
});
