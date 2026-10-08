#!/usr/bin/env node
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { chmodSync, existsSync, mkdtempSync, rmdirSync, unlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

if (!process.env.AWS_REGION) throw new Error('Set AWS_REGION and authenticate to AWS first');
const directory = mkdtempSync(path.join(tmpdir(), 'monelytics-secret-'));
const file = path.join(directory, 'secret.json');
try {
  // A temporary input file avoids putting the encryption key in command arguments or logs.
  writeFileSync(file, JSON.stringify({
    Name: 'monelytics/mfa-encryption-key',
    Description: 'Stable AES-256 encryption key for Monelytics TOTP secrets; do not rotate without re-encryption',
    SecretString: randomBytes(32).toString('base64'),
  }), { mode: 0o600 });
  if (process.platform !== 'win32') chmodSync(directory, 0o700);
  const result = spawnSync('aws', ['secretsmanager', 'create-secret', '--region', process.env.AWS_REGION, '--cli-input-json', `file://${file}`, '--query', 'ARN', '--output', 'text'], { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exitCode = result.status ?? 1;
} finally {
  if (existsSync(file)) unlinkSync(file);
  rmdirSync(directory);
}
