import { execSync } from 'node:child_process';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as url from 'node:url';

const __dirname = path.dirname(url.fileURLToPath(import.meta.url));

export const ROOT_DIR = path.resolve(__dirname, '..');
export const NODE_SDK_PACKAGE_JSON_PATH = path.join(ROOT_DIR, 'package.json');
export const NODE_SDK_PACKAGE = JSON.parse(await fs.readFile(NODE_SDK_PACKAGE_JSON_PATH, 'utf8'));
export const BUILD_DIR = path.join(ROOT_DIR, '_build');
export const BUILD_SDK_PACKAGE_DIR = path.join(BUILD_DIR, NODE_SDK_PACKAGE.name);
export const BUILD_CORE_PACKAGE_DIR = path.join(BUILD_DIR, NODE_SDK_PACKAGE.napi.packageName);

// Runs a command such as `npm` or `npx` the same way on every OS.
// - On Windows, `npm` and `npx` are `.cmd` scripts. Node can start them only
//   through a shell, so `execFileSync('npm', ...)` fails there.
// - `shell: true` with an array of arguments does not quote them, and Node 24
//   warns that it is deprecated. So each argument is quoted here, and one
//   string goes to `execSync`, which always uses a shell.
// Double quotes work in both `sh` and `cmd.exe`, but `$` and `%` can still
// expand inside them. Only pass trusted values, like script names and repo paths.
/**
 * @param {string} commandName
 * @param {string[]} args
 * @param {import('node:child_process').ExecSyncOptions} [options]
 */
export function executeCommand(commandName, args, options = {}) {
  const quotedArgs = args.map((arg) => {
    if (arg.includes('"')) {
      throw new Error(`Command argument must not contain a double quote: ${arg}`);
    }
    return `"${arg}"`;
  });
  execSync([commandName, ...quotedArgs].join(' '), {
    cwd: ROOT_DIR,
    stdio: 'inherit',
    ...options,
  });
}
