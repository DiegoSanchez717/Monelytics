import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { evaluateAudit } from './audit-policy.mjs';

const directory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
if (!process.env.npm_execpath) throw new Error('Invoke this script with npm run audit');
const result = spawnSync(process.execPath, [process.env.npm_execpath, 'audit', '--json'], {
  cwd: directory, encoding: 'utf8', maxBuffer: 10 * 1024 * 1024,
});
if (result.error) throw result.error;
if (!result.stdout) throw new Error(result.stderr || 'npm audit returned no report');
const report = JSON.parse(result.stdout);
writeFileSync(path.join(directory, 'audit-report.json'), `${JSON.stringify(report, null, 2)}\n`);
const lock = JSON.parse(readFileSync(path.join(directory, 'package-lock.json'), 'utf8'));
const evaluation = evaluateAudit(report, lock);
console.log(`Raw npm audit: ${report.metadata?.vulnerabilities?.high ?? 0} high, ${report.metadata?.vulnerabilities?.critical ?? 0} critical. Full report: infrastructure/audit-report.json`);
for (const finding of evaluation.accepted) {
  console.warn(`TEMPORARY ACCEPTED FINDING: ${finding.package}@5.0.9, trusted CDK tool globs only, expires ${evaluation.expires}. See docs/AWS-DEPLOYMENT.md.`);
}
if (evaluation.blocked.length) {
  console.error('Unaccepted high/critical dependency findings:', evaluation.blocked.map(finding => finding.package).join(', '));
  process.exitCode = 1;
}
