import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import * as path from 'node:path';

// Load the plain JavaScript policy used by CI, rather than duplicating its exception rules.
async function policy() {
  return require(path.resolve(__dirname, '../../scripts/audit-policy.mjs')).evaluateAudit as (
    report: object, lock: object, today: string,
  ) => { accepted: unknown[]; blocked: unknown[] };
}

const dependencyPath = 'node_modules/aws-cdk-lib/node_modules/brace-expansion';
const lock = { packages: {
  'node_modules/aws-cdk-lib': { version: '2.272.0' },
  [dependencyPath]: { version: '5.0.9' },
} };
const finding = {
  severity: 'high', nodes: [dependencyPath], effects: [], via: [
    { severity: 'high', url: 'https://github.com/advisories/GHSA-qhr7-859c-m2p7' },
  ],
};

test('audit exception accepts only the exact trusted bundled tool finding', async () => {
  const evaluate = await policy();
  const result = evaluate({ vulnerabilities: { 'brace-expansion': finding } }, lock, '2026-10-07');
  assert.equal(result.accepted.length, 1);
  assert.equal(result.blocked.length, 0);
});

test('audit exception fails closed on new advisories, paths, versions and expiration', async () => {
  const evaluate = await policy();
  for (const changed of [
    { ...finding, nodes: ['node_modules/brace-expansion'] },
    { ...finding, severity: 'critical' },
    { ...finding, via: [{ severity: 'high', url: 'https://github.com/advisories/GHSA-new' }] },
  ]) {
    assert.equal(evaluate({ vulnerabilities: { 'brace-expansion': changed } }, lock, '2026-10-07').blocked.length, 1);
  }
  assert.equal(evaluate({ vulnerabilities: { 'brace-expansion': finding } }, lock, '2026-11-08').blocked.length, 1);
  assert.equal(evaluate({ vulnerabilities: { 'brace-expansion': finding } }, { packages: {} }, '2026-10-07').blocked.length, 1);
});

test('another high-severity package always blocks and incomplete audit responses fail', async () => {
  const evaluate = await policy();
  assert.equal(evaluate({ vulnerabilities: { other: finding } }, lock, '2026-10-07').blocked.length, 1);
  assert.throws(() => evaluate({ error: { message: 'network failure' } }, lock, '2026-10-07'), /incomplete/);
});
