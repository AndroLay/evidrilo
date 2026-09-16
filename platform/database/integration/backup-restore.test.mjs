import assert from 'node:assert/strict';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const integrationDirectory = path.dirname(fileURLToPath(import.meta.url));
const smokeScript = path.join(integrationDirectory, 'run-local-postgres-backup-restore-smoke.sh');

function runSmoke(environment = {}) {
  return spawnSync('bash', [smokeScript], {
    encoding: 'utf8',
    env: { ...process.env, ...environment },
  });
}

test('refuses an unavailable PostgreSQL image before creating a container', () => {
  const result = runSmoke({
    EVIDRILO_POSTGRES_IMAGE: 'evidrilo-image-that-must-not-exist:0',
  });

  assert.equal(result.status, 2, `${result.stdout}\n${result.stderr}`);
  assert.match(`${result.stdout}${result.stderr}`, /image .* is not available locally/i);
});

test('round-trips the migrated local database through a custom-format dump', {
  skip: process.env.EVIDRILO_RUN_DOCKER_SMOKE !== '1',
}, () => {
  const result = runSmoke();

  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
  assert.match(result.stdout, /EVIDRILO_LOCAL_BACKUP_RESTORE_PASS/);
});
