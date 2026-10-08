import { strict as assert } from 'node:assert';
import { spawnSync } from 'node:child_process';
import * as path from 'node:path';
import { test } from 'node:test';

const repository = path.resolve(__dirname, '../../..');

function configuredBackend(flags: Record<string, string>): Record<string, string> {
  // Compose config is a local CLI operation: no engine, containers, cloud calls or
  // real .env values are required. Never print its complete environment output.
  const result = spawnSync('docker', [
    'compose', '--env-file', '.env.example', 'config', '--format', 'json',
  ], { cwd: repository, encoding: 'utf8', env: { ...process.env, ...flags }, timeout: 15_000 });
  assert.equal(result.status, 0, `Docker Compose CLI configuration failed: ${result.error?.message ?? result.stderr}`);
  return JSON.parse(result.stdout).services.backend.environment;
}

test('local Compose defaults preserve usable HTTP sessions and the synthetic demo', () => {
  const environment = configuredBackend({ COOKIE_SECURE: '', DEMO_ENABLED: '', API_DOCS_ENABLED: '' });
  assert.equal(environment.COOKIE_SECURE, 'false');
  assert.equal(environment.DEMO_ENABLED, 'true');
  assert.equal(environment.API_DOCS_ENABLED, 'true');
});

test('Compose honors operator release settings instead of silently keeping insecure demo flags', () => {
  const environment = configuredBackend({ COOKIE_SECURE: 'true', DEMO_ENABLED: 'false', API_DOCS_ENABLED: 'false' });
  assert.equal(environment.COOKIE_SECURE, 'true');
  assert.equal(environment.DEMO_ENABLED, 'false');
  assert.equal(environment.API_DOCS_ENABLED, 'false');
});
