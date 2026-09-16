import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const scriptsDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptsDirectory, '..');
const resolverPath = path.join(scriptsDirectory, 'toolchain-paths.sh');
const bashPath = process.env.BASH ?? '/bin/bash';

function runResolver(body, environment = {}, argumentsList = [repositoryRoot]) {
  return spawnSync(
    bashPath,
    ['-c', `set -e; source "$1"; ${body}`, '--', resolverPath, ...argumentsList],
    {
      cwd: repositoryRoot,
      encoding: 'utf8',
      env: { ...process.env, ...environment },
    },
  );
}

test('resolver file exists before any script consumes it', () => {
  assert.equal(fs.existsSync(resolverPath), true);
});

test('explicit Evidrilo cache variables win over standard defaults', () => {
  const result = runResolver('printf "%s\\n" "$(evidrilo_gradle_user_home)"', {
    EVIDRILO_GRADLE_USER_HOME: '/tmp/evidrilo-gradle-explicit',
    GRADLE_USER_HOME: '/tmp/standard-gradle',
  });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout.trim(), '/tmp/evidrilo-gradle-explicit');
});

test('default cache paths stay outside the repository', () => {
  const result = runResolver(
    'printf "%s\\n" "$(evidrilo_gradle_user_home)" "$(evidrilo_nuget_packages)"',
    {
      EVIDRILO_CACHE_ROOT: '/tmp/evidrilo-cache',
      GRADLE_USER_HOME: '',
      NUGET_PACKAGES: '',
    },
  );
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.stdout.trim().split('\n'), [
    '/tmp/evidrilo-cache/gradle',
    '/tmp/evidrilo-cache/nuget',
  ]);
  assert.equal(result.stdout.includes(repositoryRoot), false);
});

test('standard cache variables are used when Evidrilo overrides are absent', () => {
  const result = runResolver(
    'printf "%s\\n" "$(evidrilo_gradle_user_home)" "$(evidrilo_nuget_packages)"',
    {
      EVIDRILO_CACHE_ROOT: '',
      EVIDRILO_GRADLE_USER_HOME: '',
      EVIDRILO_NUGET_PACKAGES: '',
      GRADLE_USER_HOME: '/tmp/standard-gradle',
      NUGET_PACKAGES: '/tmp/standard-nuget',
    },
  );
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.stdout.trim().split('\n'), [
    '/tmp/standard-gradle',
    '/tmp/standard-nuget',
  ]);
});

test('repository-local dotnet fallback requires an explicit opt-in', () => {
  const fixtureRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-toolchain-fixture-'));
  const fallbackRoot = path.join(fixtureRoot, '.dotnet-local');
  fs.mkdirSync(fallbackRoot, { recursive: true });
  fs.writeFileSync(path.join(fallbackRoot, 'dotnet'), 'fixture');
  fs.chmodSync(path.join(fallbackRoot, 'dotnet'), 0o755);

  const isolatedEnvironment = {
    EVIDRILO_DOTNET_ROOT: '',
    DOTNET_ROOT: '',
    EVIDRILO_ALLOW_REPOSITORY_TOOLCHAINS: '',
    HOME: '/tmp/evidrilo-no-dotnet-home',
    PATH: '/does-not-exist',
  };
  try {
    const withoutOptIn = runResolver('evidrilo_dotnet_root "$2"', isolatedEnvironment, [fixtureRoot]);
    assert.notEqual(withoutOptIn.status, 0);

    const withOptIn = runResolver(
      'evidrilo_dotnet_root "$2"',
      { ...isolatedEnvironment, EVIDRILO_ALLOW_REPOSITORY_TOOLCHAINS: '1' },
      [fixtureRoot],
    );
    assert.equal(withOptIn.status, 0, withOptIn.stderr);
    assert.equal(withOptIn.stdout.trim(), fallbackRoot);
  } finally {
    fs.rmSync(fixtureRoot, { recursive: true, force: true });
  }
});

test('repository verifier consumes the resolver instead of forcing its old cache', () => {
  const harness = fs.readFileSync(path.join(repositoryRoot, 'scripts', 'verify-local.sh'), 'utf8');
  assert.match(harness, /source .*toolchain-paths\.sh/);
  assert.match(harness, /evidrilo_gradle_user_home/);
  assert.match(harness, /export GRADLE_USER_HOME="\$gradle_user_home"/);
  assert.doesNotMatch(harness, /--gradle-user-home ["']\$repo_root\/\.gradle-local/);
  assert.doesNotMatch(harness, /\/home\/andro\/\.local\/opt/);
});

test('integration runners consume the resolver and external NuGet path', () => {
  const apiE2e = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'integration', 'run-local-api-worker-e2e.sh'),
    'utf8',
  );
  const workerSmoke = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'worker', 'integration', 'run-local-worker-smoke.sh'),
    'utf8',
  );
  for (const script of [apiE2e, workerSmoke]) {
    assert.match(script, /source .*toolchain-paths\.sh/);
    assert.match(script, /evidrilo_dotnet_root/);
  }
  assert.doesNotMatch(apiE2e, /\.dotnet-tmp\/nuget-packages/);
});
