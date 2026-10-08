import { strict as assert } from 'node:assert';
import { spawnSync } from 'node:child_process';
import { readFileSync, statSync } from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { App } from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import { MonelyticsStack } from '../lib/monelytics-stack';

const template = () => Template.fromStack(new MonelyticsStack(new App(), 'Reference'));
const repository = path.resolve(__dirname, '../../..');

test('every reference resource has an unconditional provisioning block', () => {
  const result = template().toJSON();
  assert.deepEqual(result.Conditions.ProvisioningDisabled, { 'Fn::Equals': ['reference-only', 'never-provision'] });
  assert.ok(Object.keys(result.Resources).length > 0);
  for (const resource of Object.values(result.Resources) as { Condition?: string }[]) {
    assert.equal(resource.Condition, 'ProvisioningDisabled');
  }
});

test('reference host is bounded, encrypted and has no SSH key or inbound access', () => {
  const result = template();
  result.hasResourceProperties('AWS::EC2::Instance', {
    InstanceType: 't3.small',
    BlockDeviceMappings: [{ DeviceName: '/dev/xvda', Ebs: {
      DeleteOnTermination: true, Encrypted: true, VolumeSize: 8, VolumeType: 'gp3',
    } }],
    CreditSpecification: { CPUCredits: 'standard' }, KeyName: Match.absent(),
  });
  result.hasResourceProperties('AWS::EC2::LaunchTemplate', {
    LaunchTemplateData: Match.objectLike({ MetadataOptions: Match.objectLike({ HttpTokens: 'required' }) }),
  });
  result.hasResourceProperties('AWS::EC2::SecurityGroup', { SecurityGroupIngress: Match.absent() });
  result.resourceCountIs('AWS::EC2::SecurityGroupIngress', 0);
  result.resourceCountIs('AWS::EC2::Instance', 1);
});

test('reference has no managed database, paid network gateways or managed container services', () => {
  const result = template();
  for (const type of [
    'AWS::EC2::NatGateway', 'AWS::ElasticLoadBalancingV2::LoadBalancer', 'AWS::RDS::DBInstance',
    'AWS::ECS::Service', 'AWS::ECS::Cluster', 'AWS::ECR::Repository', 'AWS::KMS::Key',
    'AWS::SecretsManager::Secret', 'AWS::S3::Bucket', 'AWS::CloudFront::Distribution', 'AWS::Lambda::Function',
  ]) result.resourceCountIs(type, 0);
});

test('reference administration uses SSM rather than internet ingress', () => {
  template().hasResourceProperties('AWS::IAM::Role', {
    AssumeRolePolicyDocument: Match.objectLike({ Statements: Match.absent(), Statement: Match.arrayWith([
      Match.objectLike({ Principal: { Service: 'ec2.amazonaws.com' } }),
    ]) }),
    ManagedPolicyArns: Match.arrayWith([Match.objectLike({ 'Fn::Join': Match.arrayWith([
      Match.arrayWith([':iam::aws:policy/AmazonSSMManagedInstanceCore']),
    ]) })]),
  });
});

test('deployment options fail before building or invoking cloud tools', () => {
  const compiledApp = path.resolve(__dirname, '../bin/app.js');
  const before = statSync(compiledApp).mtimeMs;
  for (const option of ['--deploy', '--bootstrap', '--destroy']) {
    const result = spawnSync(process.execPath, [path.join(repository, 'scripts/deploy-aws.mjs'), option], {
      encoding: 'utf8', env: { ...process.env, AWS_REGION: 'invalid-region', PATH: '' },
    });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /zero-spend policy/);
    assert.equal(statSync(compiledApp).mtimeMs, before);
  }
});

test('CI has no cloud provisioning credentials or deployment entry', () => {
  const workflow = readFileSync(path.join(repository, '.github/workflows/ci.yml'), 'utf8');
  assert.doesNotMatch(workflow, /^\s+deploy\s*:/m);
  assert.doesNotMatch(workflow, /id-token:\s*write|configure-aws-credentials|--deploy|cdk bootstrap/);
});
