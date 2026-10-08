import { strict as assert } from 'node:assert';
import * as path from 'node:path';
import { test } from 'node:test';

const validate = require(path.resolve(__dirname, '../../../scripts/aws-free-plan-policy.mjs')).validateFreePlanState as
  (state: unknown, now: number) => { creditsUsd: number; expiresAt: string };
const now = Date.parse('2026-10-08T12:00:00Z');
const valid = {
  accountId: '123456789012', accountPlanType: 'FREE', accountPlanStatus: 'ACTIVE',
  accountPlanRemainingCredits: { amount: 73.42, unit: 'USD' },
  accountPlanExpirationDate: '2026-12-01T00:00:00Z',
};

test('read-only eligibility requires a current free plan, real credits and future expiry', () => {
  assert.deepEqual(validate(valid, now), { creditsUsd: 73.42, expiresAt: '2026-12-01T00:00:00.000Z' });
});

test('paid, legacy, expired and incomplete account information fail closed', () => {
  for (const state of [null, {}, { ...valid, accountId: 123456789012 },
    { ...valid, accountPlanType: 'PAID' }, { ...valid, accountPlanType: undefined },
    { ...valid, accountPlanStatus: 'EXPIRED' }, { ...valid, accountPlanStatus: 'NOT_STARTED' },
    { ...valid, accountPlanExpirationDate: '2026-10-08T12:00:00Z' },
    { ...valid, accountPlanExpirationDate: 'invalid' },
    { ...valid, accountPlanExpirationDate: '2027-02-31T00:00:00Z' },
    { ...valid, accountPlanExpirationDate: undefined },
  ]) assert.throws(() => validate(state, now));
});

test('missing, non-finite, non-USD and depleted credits fail closed', () => {
  for (const credits of [undefined, {}, { amount: '100', unit: 'USD' },
    { amount: NaN, unit: 'USD' }, { amount: Infinity, unit: 'USD' },
    { amount: 0, unit: 'USD' }, { amount: -1, unit: 'USD' }, { amount: 100, unit: 'EUR' },
  ]) assert.throws(() => validate({ ...valid, accountPlanRemainingCredits: credits }, now));
});
