import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const checker = path.join(repositoryRoot, 'scripts', 'worktrees', 'check-worktree-scope.sh');

function run(...args) {
  return spawnSync('bash', [checker, ...args], { cwd: repositoryRoot, encoding: 'utf8' });
}

test('allows a frontend-owned path in the frontend lane', () => {
  const result = run('frontend', '--paths', 'apps/mobile-shared/src/commonMain/kotlin/dev/nextgen/mobile/EvidriloApp.kt');
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /WORKTREE_SCOPE_PASS/);
});

test('rejects a backend path from the frontend lane and names the owner', () => {
  const result = run('frontend', '--paths', 'platform/api/Program.cs');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /WORKTREE_SCOPE_VIOLATION/);
  assert.match(result.stderr, /owner=backend/);
});

test('allows a backend-owned path in the backend lane', () => {
  const result = run('backend', '--paths', 'platform/api/Program.cs');
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /WORKTREE_SCOPE_PASS/);
});

test('rejects a frontend path from the backend lane and names the owner', () => {
  const result = run('backend', '--paths', 'apps/android/src/main/AndroidManifest.xml');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /WORKTREE_SCOPE_VIOLATION/);
  assert.match(result.stderr, /owner=frontend/);
});

test('requires an explicit structural override for main moves', () => {
  const structuralPath = 'apps/mobile-shared/src/commonMain/kotlin/dev/nextgen/mobile/surfaces/Example.kt';
  const blocked = run('main', '--paths', structuralPath);
  assert.notEqual(blocked.status, 0);
  const allowed = run('main', '--allow-structural', '--paths', structuralPath);
  assert.equal(allowed.status, 0, `${allowed.stdout}${allowed.stderr}`);
});

test('rejects retired lane names', () => {
  const result = run('domain', '--paths', 'modules/domain/src/commonMain/kotlin/dev/nextgen/mobile/domain/Domain.kt');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unknown lane: domain/);
});
