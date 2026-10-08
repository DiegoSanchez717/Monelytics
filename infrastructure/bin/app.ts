#!/usr/bin/env node
import { App, Tags } from 'aws-cdk-lib';
import { MonelyticsStack } from '../lib/monelytics-stack';

const app = new App();
const stack = new MonelyticsStack(app, 'Monelytics', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION ?? process.env.AWS_REGION ?? 'us-east-1',
  },
  description: 'Monelytics: private PostgreSQL, Spring Boot on ECS, Angular on S3/CloudFront',
});
Tags.of(stack).add('Application', 'Monelytics');
Tags.of(stack).add('ManagedBy', 'AWS-CDK');
