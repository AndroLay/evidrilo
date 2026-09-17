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

test('allows a domain-owned path in the domain lane', () => {
  const result = run('domain', '--paths', 'composeApp/src/commonMain/kotlin/dev/nextgen/mobile/domain/access/EntitlementIds.kt');
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /WORKTREE_SCOPE_PASS/);
});

test('rejects a platform path from the domain lane and names the owner', () => {
  const result = run('domain', '--paths', 'platform/api/Program.cs');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /WORKTREE_SCOPE_VIOLATION/);
  assert.match(result.stderr, /owner=platform/);
});

test('requires an explicit structural override for integration moves', () => {
  const structuralPath = 'composeApp/src/commonMain/kotlin/dev/nextgen/mobile/surfaces/Example.kt';
  const blocked = run('integration', '--paths', structuralPath);
  assert.notEqual(blocked.status, 0);
  const allowed = run('integration', '--allow-structural', '--paths', structuralPath);
  assert.equal(allowed.status, 0, `${allowed.stdout}${allowed.stderr}`);
});
