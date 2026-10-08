#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const arguments_ = process.argv.slice(2);
// This guard runs before build, credentials, CLI execution or any AWS side effect.
if (arguments_.some(argument => argument !== '--quiet')) {
  console.error('AWS provisioning is disabled by the zero-spend policy. This command supports synthesis only.');
  process.exit(1);
}
const infrastructure = resolve(dirname(fileURLToPath(import.meta.url)), '../infrastructure');
for (const script of ['scripts/build.mjs', 'dist/bin/app.js']) {
  const result = spawnSync(process.execPath, [join(infrastructure, script)], {
    cwd: infrastructure, stdio: 'inherit',
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
if (!arguments_.includes('--quiet')) {
  console.log('Offline reference synthesized to infrastructure/cdk.out/MonelyticsReference.template.json. No AWS resources created.');
}
