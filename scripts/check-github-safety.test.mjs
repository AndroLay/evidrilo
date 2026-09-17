import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const scriptsDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.dirname(scriptsDirectory);
const checker = path.join(scriptsDirectory, 'check-github-safety.sh');

function runGit(root, args) {
  const result = spawnSync('git', ['-C', root, ...args], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  return result;
}

function createFixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-github-safety-'));
  fs.copyFileSync(path.join(repositoryRoot, '.gitignore'), path.join(root, '.gitignore'));
  for (const directory of [
    '.github',
    'apps/android',
    'apps/mobile-shared',
    'infra',
    'gradle',
    'apps/ios',
    'modules/domain',
    'platform',
    'scripts',
    'tests',
    'tooling',
    'examples',
    'docs/architecture',
    'docs/product',
    'docs/licenses',
    'docs/operations',
  ]) {
    fs.mkdirSync(path.join(root, directory), { recursive: true });
  }
  for (const file of [
    'README.md',
    'LICENSE',
    'CONTRIBUTING.md',
    'SECURITY.md',
    'CHANGELOG.md',
    'THIRD_PARTY_NOTICES.md',
    '.editorconfig',
    '.gitattributes',
    '.dockerignore',
    'gradlew',
    'gradle.properties',
    'build.gradle.kts',
    'build.gradle.kts',
    'settings.gradle.kts',
    'local.properties.example',
    '.github/workflows/verify.yml',
    'apps/android/src.txt',
    'apps/mobile-shared/src.txt',
    'infra/README.md',
    'gradle/libs.versions.toml',
    'apps/ios/README.md',
    'platform/README.md',
    'scripts/check-github-safety.sh',
    'modules/domain/README.md',
    'tests/architecture/README.md',
    'tooling/lint/README.md',
    'examples/README.md',
    'docs/operations/README.md',
    'docs/README.md',
    'docs/decisions.md',
    'docs/development/README.md',
    'docs/release.md',
    'docs/roadmap.md',
    'docs/testing.md',
    'docs/architecture/revenuecat.md',
    'docs/architecture/platform-decision.md',
    'docs/architecture/repository-structure.md',
    'docs/licenses/SourceSans3-OFL-1.1.md',
    'docs/licenses/audio-assets.md',
    'docs/product/m0-product-contract.md',
  ]) {
    fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
    fs.writeFileSync(path.join(root, file), 'synthetic public fixture\n');
  }
  runGit(root, ['init', '--quiet']);
  runGit(root, ['config', 'user.email', 'evidrilo-test@example.invalid']);
  runGit(root, ['config', 'user.name', 'Evidrilo Safety Test']);
  runGit(root, ['add', '.']);
  runGit(root, ['commit', '--quiet', '-m', 'fixture']);
  return root;
}

function runChecker(root) {
  return spawnSync('bash', [checker, root], { encoding: 'utf8' });
}

test('accepts a clean public Git index and ignored private probes', () => {
  const root = createFixture();
  try {
    const result = runChecker(root);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /GITHUB_SAFETY_CHECK_PASS/);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('ignores root compiler-report output before git add', () => {
  const root = createFixture();
  try {
    fs.mkdirSync(path.join(root, '.tmp'), { recursive: true });
    fs.writeFileSync(path.join(root, '.tmp', 'compiler-report.json'), 'generated local output\n');
    const result = runChecker(root);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /GITHUB_SAFETY_CHECK_PASS/);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('rejects a force-added root generated-output path', () => {
  const root = createFixture();
  try {
    fs.mkdirSync(path.join(root, '.tmp'), { recursive: true });
    fs.writeFileSync(path.join(root, '.tmp', 'compiler-report.json'), 'generated local output\n');
    runGit(root, ['add', '--force', '.tmp/compiler-report.json']);
    const result = runChecker(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /generated|\.tmp/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('rejects an untracked non-ignored file before git add', () => {
  const root = createFixture();
  try {
    fs.writeFileSync(path.join(root, 'private-note.md'), 'local note\n');
    const result = runChecker(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /untracked|eligible/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('rejects a force-added private design path', () => {
  const root = createFixture();
  try {
    fs.mkdirSync(path.join(root, 'design'), { recursive: true });
    fs.writeFileSync(path.join(root, 'design', 'mockup.png'), 'private design\n');
    runGit(root, ['add', '--force', 'design/mockup.png']);
    const result = runChecker(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /forbidden|private|design/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('rejects a force-added internal design path', () => {
  const root = createFixture();
  try {
    fs.mkdirSync(path.join(root, 'internal', 'design'), { recursive: true });
    fs.writeFileSync(path.join(root, 'internal', 'design', 'mockup.png'), 'private design\n');
    runGit(root, ['add', '--force', 'internal/design/mockup.png']);
    const result = runChecker(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /forbidden|private|internal|design/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('rejects a force-added machine-specific Apple configuration', () => {
  const root = createFixture();
  try {
    fs.mkdirSync(path.join(root, 'apps/ios', 'Configuration'), { recursive: true });
    fs.writeFileSync(path.join(root, 'apps/ios', 'Configuration', 'Debug.xcconfig'), 'SUPABASE_URL=local-only\n');
    runGit(root, ['add', '--force', 'apps/ios/Configuration/Debug.xcconfig']);
    const result = runChecker(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /credential|configuration|xcconfig|forbidden/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('rejects credential-shaped content without echoing its value', () => {
  const root = createFixture();
  const syntheticSecret = 'synthetic-secret-value-that-must-not-appear';
  try {
    fs.appendFileSync(path.join(root, 'README.md'), `\nservice_role=${syntheticSecret}\n`);
    runGit(root, ['add', 'README.md']);
    const result = runChecker(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /credential|content|secret/i);
    assert.doesNotMatch(`${result.stdout}${result.stderr}`, new RegExp(syntheticSecret));
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('blocks clean safety verification when Git metadata is unavailable', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-github-no-git-'));
  try {
    const result = runChecker(root);
    assert.equal(result.status, 2);
    assert.match(`${result.stdout}${result.stderr}`, /usable Git metadata|required|exporter/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
