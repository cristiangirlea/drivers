import { executeCommand } from '../../scripts/common.mjs';

export default function setup() {
  // WIF unpacks a prebuilt addon; local runs may already have `_build`.
  if (process.env.SKIP_NODEJS_BUILD === 'true') {
    return;
  }

  // Build and link for both e2e and e2e-old-driver to resolve SDK types in tests.
  for (const script of ['build:core', 'build:sdk', 'build:link-local']) {
    executeCommand('npm', ['run', script]);
  }
}
