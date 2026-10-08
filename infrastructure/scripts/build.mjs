import { spawnSync } from 'node:child_process';
import { lstatSync, realpathSync, rmSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const infrastructure = realpathSync(resolve(dirname(fileURLToPath(import.meta.url)), '..'));
const normalized = value => process.platform === 'win32' ? value.toLowerCase() : value;
const outputs = ['dist', 'cdk.out'].map(name => ({ name, target: resolve(infrastructure, name) }));
// Check every absolute target before deletion; never follow junctions, including dangling ones.
for (const { name, target } of outputs) {
  if (relative(infrastructure, target) !== name) throw new Error('Refusing an unsafe build output path');
  const metadata = lstatSync(target, { throwIfNoEntry: false });
  if (metadata && (!metadata.isDirectory() || metadata.isSymbolicLink() || normalized(realpathSync(target)) !== normalized(target))) {
    throw new Error('Refusing to clean a linked or unexpected build output');
  }
}
for (const { target } of outputs) rmSync(target, { recursive: true, force: true });
const compiler = join(infrastructure, 'node_modules/typescript/bin/tsc');
const result = spawnSync(process.execPath, [compiler], { cwd: infrastructure, stdio: 'inherit' });
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
