import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.dirname(scriptsDirectory);
const checker = path.join(scriptsDirectory, 'check-version-alignment.sh');

function runChecker(root) {
  return spawnSync('bash', [checker, root], { encoding: 'utf8' });
}

function createFixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-version-'));
  const files = [
    'version.props',
    'Directory.Build.props',
    'build.gradle.kts',
    'apps/android/build.gradle.kts',
    'apps/ios/Configuration/Config.xcconfig',
    'apps/ios/Configuration/Release.xcconfig.example',
    'infra/docker/api.Dockerfile',
    'infra/docker/worker.Dockerfile',
  ];
  for (const relativePath of files) {
    const destination = path.join(root, relativePath);
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.copyFileSync(path.join(repositoryRoot, relativePath), destination);
  }
  return root;
}

test('current release version is aligned across build surfaces', () => {
  const result = runChecker(repositoryRoot);
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /VERSION_ALIGNMENT_PASS/);
});

test('rejects marketing-version drift without exposing unrelated values', () => {
  const root = createFixture();
  try {
    const releaseConfig = path.join(root, 'apps/ios/Configuration/Release.xcconfig.example');
    fs.writeFileSync(
      releaseConfig,
      fs.readFileSync(releaseConfig, 'utf8').replace('MARKETING_VERSION = 0.1.0', 'MARKETING_VERSION = 9.9.9'),
    );
    const result = runChecker(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /VERSION_ALIGNMENT_FAIL|MARKETING_VERSION/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
