#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const infrastructure = path.join(root, 'infrastructure');
const execute = process.argv.includes('--deploy');
const required = ['AWS_REGION', 'API_ORIGIN_DOMAIN', 'ALB_CERTIFICATE_ARN', 'MFA_SECRET_ARN', 'ORIGIN_HEADER_SECRET'];
for (const name of required) {
  if (!process.env[name]) throw new Error(`Set ${name}; see docs/AWS-DEPLOYMENT.md`);
}
if (!/^[A-Za-z0-9_-]{32,128}$/.test(process.env.ORIGIN_HEADER_SECRET)) {
  throw new Error('ORIGIN_HEADER_SECRET must contain 32–128 random letters, numbers, underscores or hyphens');
}
for (const name of ['ALB_CERTIFICATE_ARN', 'MFA_SECRET_ARN']) {
  if (process.env[name].split(':')[3] !== process.env.AWS_REGION) {
    throw new Error(`${name} must reference the deployment AWS_REGION`);
  }
}
if (process.env.HOSTED_ZONE_NAME) {
  const zone = process.env.HOSTED_ZONE_NAME.replace(/\.$/, '').toLowerCase();
  for (const domain of [process.env.API_ORIGIN_DOMAIN, process.env.WEB_DOMAIN].filter(Boolean)) {
    if (domain.toLowerCase() !== zone && !domain.toLowerCase().endsWith(`.${zone}`)) {
      throw new Error(`Configured domain ${domain} must belong to HOSTED_ZONE_NAME`);
    }
  }
}

function run(command, args, cwd = root, capture = false) {
  // Arguments are passed directly to the process, without shell interpolation of secrets.
  const result = spawnSync(command, args, { cwd, env: process.env, stdio: capture ? 'pipe' : 'inherit', encoding: 'utf8' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${path.basename(command)} failed with exit code ${result.status}${capture ? `: ${result.stderr}` : ''}`);
  return result.stdout;
}

const cdk = path.join(infrastructure, 'node_modules/aws-cdk/bin/cdk');
const tsc = path.join(infrastructure, 'node_modules/typescript/bin/tsc');
if (!existsSync(cdk) || !existsSync(tsc)) throw new Error('Run npm ci --prefix infrastructure first');
run(process.execPath, [tsc], infrastructure);
const context = [];
const contextSettings = {
  HOSTED_ZONE_ID: 'hostedZoneId', HOSTED_ZONE_NAME: 'hostedZoneName',
  WEB_DOMAIN: 'webDomain', WEB_CERTIFICATE_ARN: 'webCertificateArn',
};
for (const [environmentName, contextName] of Object.entries(contextSettings)) {
  if (process.env[environmentName]) context.push('--context', `${contextName}=${process.env[environmentName]}`);
}
if (!execute) {
  run(process.execPath, [cdk, 'synth', 'Monelytics', '--quiet', ...context], infrastructure);
  console.log('Synthesized only. Add --deploy to create or update billed AWS resources.');
  process.exit(0);
}

const parameters = [
  ['ApiOriginDomain', 'API_ORIGIN_DOMAIN'], ['AlbCertificateArn', 'ALB_CERTIFICATE_ARN'],
  ['MfaSecretArn', 'MFA_SECRET_ARN'], ['OriginHeaderSecret', 'ORIGIN_HEADER_SECRET'],
].flatMap(([parameter, environmentName]) => ['--parameters', `Monelytics:${parameter}=${process.env[environmentName]}`]);
run(process.execPath, [path.join(root, 'frontend/node_modules/@angular/cli/bin/ng.js'), 'build', '--configuration=production'], path.join(root, 'frontend'));
run(process.execPath, [cdk, 'deploy', 'Monelytics', '--require-approval', 'never', ...context, ...parameters], infrastructure);

const output = JSON.parse(run('aws', [
  'cloudformation', 'describe-stacks', '--stack-name', 'Monelytics', '--region', process.env.AWS_REGION,
  '--query', 'Stacks[0].Outputs', '--output', 'json',
], root, true));
const values = Object.fromEntries(output.map(entry => [entry.OutputKey, entry.OutputValue]));
if (!values.WebsiteBucket || !values.DistributionId) throw new Error('Missing stack deployment outputs');
const bundle = path.join(root, 'frontend/dist/monelytics/browser');
run('aws', ['s3', 'sync', bundle, `s3://${values.WebsiteBucket}`, '--delete', '--exclude', 'index.html', '--cache-control', 'public,max-age=300']);
run('aws', ['s3', 'cp', path.join(bundle, 'index.html'), `s3://${values.WebsiteBucket}/index.html`, '--cache-control', 'no-cache,no-store,must-revalidate', '--content-type', 'text/html']);
run('aws', ['cloudfront', 'create-invalidation', '--distribution-id', values.DistributionId, '--paths', '/*']);
console.log(`Deployment complete: ${values.WebsiteUrl}`);
