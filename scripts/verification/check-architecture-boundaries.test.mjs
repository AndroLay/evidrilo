import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const checker = path.join(repositoryRoot, 'scripts', 'verification', 'check-architecture-boundaries.sh');

test('current staged architecture satisfies the domain and feature dependency guard', () => {
  const result = spawnSync('bash', [checker, repositoryRoot], { encoding: 'utf8' });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /ARCHITECTURE_BOUNDARY_PASS/);
});
