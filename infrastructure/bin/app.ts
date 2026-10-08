#!/usr/bin/env node
import { App, Tags } from 'aws-cdk-lib';
import { WealthPathStack } from '../lib/wealthpath-stack';

const app = new App();
const stack = new WealthPathStack(app, 'WealthPath', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION ?? process.env.AWS_REGION ?? 'us-east-1',
  },
  description: 'WealthPath: private PostgreSQL, Spring Boot on ECS, Angular on S3/CloudFront',
});
Tags.of(stack).add('Application', 'WealthPath');
Tags.of(stack).add('ManagedBy', 'AWS-CDK');
