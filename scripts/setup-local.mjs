import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

export function setupLocal() {
  const root = fileURLToPath(new URL('../', import.meta.url));
  const target = resolve(root, '.env');
  // Never rotate an existing MFA encryption key implicitly: enrolled authenticators depend on it.
  if (existsSync(target)) {
    console.log('Using existing .env configuration.');
    return root;
  }
  const example = readFileSync(resolve(root, '.env.example'), 'utf8');
  const contents = example
    .replace(/^DATABASE_PASSWORD=.*$/m, `DATABASE_PASSWORD=${randomBytes(24).toString('hex')}`)
    .replace(/^MFA_ENCRYPTION_KEY=.*$/m, `MFA_ENCRYPTION_KEY=${randomBytes(32).toString('base64')}`);
  writeFileSync(target, contents, { mode: 0o600, flag: 'wx' });
  console.log('Created local .env with random database and MFA secrets. Demo credentials are documented in README.');
  return root;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) setupLocal();
