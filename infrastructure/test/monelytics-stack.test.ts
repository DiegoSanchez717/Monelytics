import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { App } from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import { MonelyticsStack } from '../lib/monelytics-stack';

const template = () => Template.fromStack(new MonelyticsStack(new App(), 'TestStack', {
  env: { account: '123456789012', region: 'us-east-1' },
}));

test('database is encrypted, private, backed up and protected from deletion', () => {
  template().hasResourceProperties('AWS::RDS::DBInstance', {
    Engine: 'postgres', EngineVersion: '17.11', PubliclyAccessible: false, StorageEncrypted: true,
    MultiAZ: true, BackupRetentionPeriod: 7, DeletionProtection: true,
  });
});

test('application containers receive secrets by reference and production defaults', () => {
  const result = template();
  result.hasResourceProperties('AWS::ECS::TaskDefinition', {
    RuntimePlatform: { CpuArchitecture: 'X86_64', OperatingSystemFamily: 'LINUX' },
    ContainerDefinitions: Match.arrayWith([Match.objectLike({
      Name: 'api',
      Environment: Match.arrayWith([
        { Name: 'COOKIE_SECURE', Value: 'true' },
        { Name: 'DEMO_ENABLED', Value: 'false' },
      ]),
      Secrets: Match.arrayWith([
        Match.objectLike({ Name: 'DATABASE_PASSWORD' }),
        Match.objectLike({ Name: 'MFA_ENCRYPTION_KEY' }),
      ]),
    })]),
  });
  result.hasResourceProperties('AWS::ECS::Service', { DesiredCount: 2, NetworkConfiguration: {
    AwsvpcConfiguration: Match.objectLike({ AssignPublicIp: 'DISABLED' }),
  } });
});

test('CloudFront API paths forward sessions without caching or SPA rewriting', () => {
  template().hasResourceProperties('AWS::CloudFront::Distribution', {
    DistributionConfig: Match.objectLike({
      CacheBehaviors: Match.arrayWith([Match.objectLike({
        PathPattern: '/api/*',
        CachePolicyId: '4135ea2d-6df8-44a3-9df3-4b5a84be39ad',
        OriginRequestPolicyId: 'b689b0a8-53d0-40ab-baf2-68738e2966ac',
        ViewerProtocolPolicy: 'https-only',
        AllowedMethods: ['GET', 'HEAD', 'OPTIONS', 'PUT', 'PATCH', 'POST', 'DELETE'],
      })]),
      Origins: Match.arrayWith([Match.objectLike({
        CustomOriginConfig: Match.objectLike({ OriginProtocolPolicy: 'https-only' }),
      })]),
      CustomErrorResponses: Match.absent(),
    }),
  });
});

test('static bucket blocks public access and ALB rejects direct requests', () => {
  const result = template();
  result.hasResourceProperties('AWS::S3::Bucket', {
    PublicAccessBlockConfiguration: {
      BlockPublicAcls: true, BlockPublicPolicy: true, IgnorePublicAcls: true, RestrictPublicBuckets: true,
    },
  });
  result.hasResourceProperties('AWS::ElasticLoadBalancingV2::Listener', {
    Port: 443, Protocol: 'HTTPS',
    DefaultActions: [{ Type: 'fixed-response', FixedResponseConfig: {
      ContentType: 'text/plain', MessageBody: 'Access denied', StatusCode: '403',
    } }],
  });
  result.resourceCountIs('AWS::CloudFront::OriginAccessControl', 1);
});

test('static assets cache independently while default HTML remains uncached', () => {
  template().hasResourceProperties('AWS::CloudFront::Distribution', {
    DistributionConfig: Match.objectLike({
      DefaultCacheBehavior: Match.objectLike({ CachePolicyId: '4135ea2d-6df8-44a3-9df3-4b5a84be39ad' }),
      CacheBehaviors: Match.arrayWith([Match.objectLike({
        PathPattern: '*.js', CachePolicyId: '658327ea-f89d-4fab-a63d-7e88639e58f6',
      })]),
    }),
  });
});

test('custom viewer domain requires the correct certificate region', () => {
  assert.throws(() => new MonelyticsStack(new App({ context: { webDomain: 'monelytics.example.com' } }), 'Invalid'), /supplied together/);
  assert.throws(() => new MonelyticsStack(new App({ context: {
    webDomain: 'monelytics.example.com', webCertificateArn: 'arn:aws:acm:us-west-2:123456789012:certificate/test',
  } }), 'InvalidRegion'), /us-east-1/);
});

test('custom DNS aliases preserve the complete origin name parameter and frontend certificate', () => {
  const viewerCertificate = 'arn:aws:acm:us-east-1:123456789012:certificate/00000000-0000-0000-0000-000000000001';
  const app = new App({ context: {
    webDomain: 'monelytics.example.com', webCertificateArn: viewerCertificate,
    hostedZoneId: 'Z1234567890', hostedZoneName: 'example.com',
  } });
  const result = Template.fromStack(new MonelyticsStack(app, 'CustomDomain', {
    env: { account: '123456789012', region: 'us-east-1' },
  }));
  result.resourceCountIs('AWS::Route53::RecordSet', 2);
  result.hasResourceProperties('AWS::Route53::RecordSet', {
    Type: 'A', Name: { 'Fn::Join': ['', [{ Ref: 'ApiOriginDomain' }, '.']] },
  });
  result.hasResourceProperties('AWS::CloudFront::Distribution', {
    DistributionConfig: Match.objectLike({
      Aliases: ['monelytics.example.com'],
      ViewerCertificate: Match.objectLike({ AcmCertificateArn: viewerCertificate }),
    }),
  });
});
