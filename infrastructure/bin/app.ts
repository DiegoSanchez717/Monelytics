#!/usr/bin/env node
import { App, Tags } from 'aws-cdk-lib';
import { MonelyticsStack } from '../lib/monelytics-stack';

// Environment-agnostic synthesis uses CloudFormation tokens, never AWS credential lookups.
const app = new App({ outdir: 'cdk.out', autoSynth: false });
const stack = new MonelyticsStack(app, 'MonelyticsReference', {
  description: 'Monelytics synth-only EC2/Docker Compose reference; every resource is disabled',
});
Tags.of(stack).add('Application', 'Monelytics');
Tags.of(stack).add('Purpose', 'OfflineReference');
app.synth();
