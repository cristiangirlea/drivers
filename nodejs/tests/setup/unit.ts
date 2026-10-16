import { executeCommand } from '../../scripts/common.mjs';

export default function setup() {
  for (const script of ['build:core', 'build:link-local']) {
    executeCommand('npm', ['run', script]);
  }
}
