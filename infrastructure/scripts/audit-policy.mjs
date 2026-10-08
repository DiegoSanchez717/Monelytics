// Temporary exception for a bundled CDK deployment dependency, never application runtime code.
const exception = {
  package: 'brace-expansion',
  version: '5.0.9',
  dependencyPath: 'node_modules/aws-cdk-lib/node_modules/brace-expansion',
  cdkVersion: '2.272.0',
  expires: '2026-11-07',
  advisoryUrls: new Set([
    'https://github.com/advisories/GHSA-q2hr-2g5m-vwhr',
    'https://github.com/advisories/GHSA-qhr7-859c-m2p7',
    'https://github.com/advisories/GHSA-6j4f-fj2g-mc7p',
  ]),
};

export function evaluateAudit(report, lock, today = new Date().toISOString().slice(0, 10)) {
  if (report.error || !report.vulnerabilities || !lock.packages) throw new Error('Audit response or lockfile is incomplete');
  const accepted = [];
  const blocked = [];
  for (const [name, finding] of Object.entries(report.vulnerabilities)) {
    if (!['high', 'critical'].includes(finding.severity)) continue;
    const scopedException = name === exception.package
      && finding.severity === 'high'
      && today <= exception.expires
      && lock.packages['node_modules/aws-cdk-lib']?.version === exception.cdkVersion
      && lock.packages[exception.dependencyPath]?.version === exception.version
      && finding.nodes?.length === 1
      && finding.nodes[0] === exception.dependencyPath
      && finding.effects?.length === 0
      && finding.via?.length > 0
      && finding.via.every(advisory => typeof advisory === 'object'
        && ['moderate', 'high'].includes(advisory.severity)
        && exception.advisoryUrls.has(advisory.url));
    (scopedException ? accepted : blocked).push({ package: name, ...finding });
  }
  return { accepted, blocked, expires: exception.expires };
}
