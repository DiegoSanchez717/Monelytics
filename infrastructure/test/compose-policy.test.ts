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

test('hosted AI defaults never enable an unconfirmed or credential-free account', () => {
  const environment = configuredBackend({ AI_API_KEY: '', GROQ_API_KEY: '', AI_FREE_PLAN_CONFIRMED: '' });
  assert.equal(environment.AI_PROVIDER, 'groq');
  assert.equal(environment.AI_API_KEY, '');
  assert.equal(environment.AI_FREE_PLAN_CONFIRMED, 'false');
  assert.equal(environment.AI_DAILY_REQUEST_LIMIT, '40');
  assert.equal(environment.AI_MODEL, 'openai/gpt-oss-20b');
});

test('Compose forwards explicit Free-plan confirmation and a bounded hosted request limit', () => {
  const environment = configuredBackend({ AI_FREE_PLAN_CONFIRMED: 'true', AI_DAILY_REQUEST_LIMIT: '12', GROQ_API_KEY: 'synthetic-groq-fixture', AI_API_KEY: 'synthetic-alias-fixture' });
  assert.equal(environment.AI_FREE_PLAN_CONFIRMED, 'true');
  assert.equal(environment.AI_DAILY_REQUEST_LIMIT, '12');
  assert.equal(environment.AI_API_KEY, 'synthetic-groq-fixture');
});
