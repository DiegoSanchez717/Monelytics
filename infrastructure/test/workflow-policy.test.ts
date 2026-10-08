import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';

const workflow = readFileSync(path.resolve(__dirname, '../../../.github/workflows/ci.yml'), 'utf8');

// This repository deliberately uses a small, explicit workflow shape. Reject changes
// that could allocate metered runners, store artifacts, publish or acquire write access.
function assertZeroSpendWorkflow(source: string): void {
  assert.match(source, /^permissions:\r?\n  contents: read\s*\r?\n/m);
  assert.equal((source.match(/^\s*permissions:/gm) ?? []).length, 1);
  assert.doesNotMatch(source, /:\s*['"]?(?:write|write-all)['"]?\s*$/m);
  assert.doesNotMatch(source, /configure-aws-credentials|id-token:/);

  const jobsSection = source.split(/^jobs:\s*\r?\n/m)[1];
  assert.ok(jobsSection, 'an explicit jobs section is required');
  const jobs = [...jobsSection.matchAll(/^  ([a-z][a-z0-9-]*):\r?\n([\s\S]*?)(?=^  [a-z][a-z0-9-]*:\r?$|(?![\s\S]))/gm)];
  assert.equal(jobs.length, 5, 'review the policy before adding another job');
  for (const [, name, body] of jobs) {
    assert.match(body, /^    if: github\.event\.repository\.private == false\r?$/m, `${name} must skip before runner allocation`);
    assert.match(body, /^    runs-on: ubuntu-24\.04\r?$/m, `${name} must use the free standard public runner`);
    assert.equal((body.match(/^    runs-on:/gm) ?? []).length, 1);
  }

  const allowedActions = new Set([
    'actions/checkout', 'actions/setup-node', 'actions/setup-java',
    'docker/setup-buildx-action', 'aquasecurity/trivy-action',
  ]);
  for (const [, action, revision] of source.matchAll(/uses:\s*([^\s@]+)@([^\s#]+)/g)) {
    assert.ok(allowedActions.has(action), `action ${action} needs zero-spend review`);
    assert.match(revision, /^[a-f0-9]{40}$/, 'actions must be pinned to a reviewed commit');
  }
  assert.doesNotMatch(source, /upload-artifact|actions\/cache|cache-dependency-path|cache-(?:to|from):|snapshot:/);
  for (const [, value] of source.matchAll(/^\s+cache:\s*(.+)\r?$/gm)) {
    assert.match(value.trim(), /^['"]?false['"]?$/, 'all action caches must be explicitly disabled');
  }
  assert.match(source, /uses: aquasecurity\/trivy-action@[a-f0-9]{40}[\s\S]*?cache: 'false'/);
  assert.doesNotMatch(source, /docker\s+push|aws\s+|cdk\s+(?:deploy|bootstrap|destroy)|--deploy|push:\s*true/);
  assert.match(source, /Print unmodified infrastructure dependency audit/);
}

test('CI validates only on standard public runners with read-only access and no paid storage', () => {
  assertZeroSpendWorkflow(workflow);
});

test('workflow policy rejects private jobs and larger or dynamically selected runners', () => {
  for (const modified of [
    workflow.replace('if: github.event.repository.private == false', 'if: always()'),
    workflow.replace('runs-on: ubuntu-24.04', 'runs-on: ubuntu-24.04-16core'),
    workflow.replace('runs-on: ubuntu-24.04', 'runs-on: ${{ inputs.runner }}'),
  ]) assert.throws(() => assertZeroSpendWorkflow(modified));
});

test('workflow policy rejects artifact uploads and explicitly or implicitly enabled caches', () => {
  for (const modified of [
    workflow.replace('actions/checkout@', 'actions/upload-artifact@'),
    workflow.replace("cache: 'false'", "cache: 'true'"),
    workflow.replace("          cache: 'false'\n", '').replace("          cache: 'false'\r\n", ''),
    workflow.replace("node-version: '24.12.0'", "node-version: '24.12.0'\n          cache: npm"),
  ]) assert.throws(() => assertZeroSpendWorkflow(modified));
});

test('workflow policy rejects write permissions, publishing and provisioning paths', () => {
  for (const modified of [
    workflow.replace('contents: read', 'contents: write'),
    workflow.replace('actions/checkout@', 'aws-actions/configure-aws-credentials@'),
    workflow.replace('run: npm run build', 'run: docker push example/application:latest'),
    workflow.replace('run: npm run synth -- --quiet', 'run: aws cloudformation deploy'),
  ]) assert.throws(() => assertZeroSpendWorkflow(modified));
});
