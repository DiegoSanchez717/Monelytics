#!/usr/bin/env node
import { randomBytes } from 'node:crypto';
import { existsSync, lstatSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.argv.length !== 2) throw new Error('This command only generates a local key; it accepts no cloud options');
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const directory = join(root, '.local-secrets');
if (existsSync(directory) && lstatSync(directory).isSymbolicLink()) throw new Error('Refusing a linked secret directory');
mkdirSync(directory, { recursive: true, mode: 0o700 });
const target = join(directory, 'mfa-encryption.key');
// Never overwrite a stable encryption key or modify an existing .env implicitly.
writeFileSync(target, `${randomBytes(32).toString('base64')}\n`, { mode: 0o600, flag: 'wx' });
console.log('Generated .local-secrets/mfa-encryption.key locally. Keep it private; no cloud service was called.');
