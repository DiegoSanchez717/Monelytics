#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { validateFreePlanState } from './aws-free-plan-policy.mjs';

if (process.argv.length !== 2) throw new Error('Use AWS_PROFILE for credentials; this read-only command accepts no options');
// The operation and endpoint Region are fixed: this tool cannot upgrade plans or provision services.
const result = spawnSync('aws', [
  'freetier', 'get-account-plan-state', '--region', 'us-east-1', '--output', 'json',
  '--no-cli-pager', '--no-cli-auto-prompt',
], {
  // Only AWS's official endpoint can supply account-plan eligibility.
  env: { ...process.env, AWS_IGNORE_CONFIGURED_ENDPOINT_URLS: 'true' },
  encoding: 'utf8', stdio: 'pipe', timeout: 15000, maxBuffer: 1024 * 1024,
});
if (result.error || result.status !== 0) {
  console.error('Could not verify AWS free-plan state. Install a current AWS CLI v2 and grant freetier:GetAccountPlanState; no resources created.');
  process.exit(1);
}
try {
  const state = validateFreePlanState(JSON.parse(result.stdout));
  console.log(`Active FREE plan verified: USD ${state.creditsUsd.toFixed(2)} credits; expires ${state.expiresAt}.`);
  console.log('This is a read-only eligibility check, not deployment approval. Provisioning remains disabled.');
} catch (error) {
  console.error(`${error.message}. No resources created.`);
  process.exitCode = 1;
}
