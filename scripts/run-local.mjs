import { spawnSync } from 'node:child_process';
import { setupLocal } from './setup-local.mjs';

const root = setupLocal();
const result = spawnSync('docker', ['compose', 'up', '--build', '--wait', '--wait-timeout', '300'], {
  cwd: root, stdio: 'inherit', shell: false,
});
if (result.error) console.error(result.error.message);
if (result.status === 0) console.log('Monelytics is ready at http://localhost:8080 (or FRONTEND_PORT from .env).');
process.exit(result.status ?? 1);
