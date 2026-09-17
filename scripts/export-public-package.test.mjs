import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const scriptsDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.dirname(scriptsDirectory);
const exporter = path.join(scriptsDirectory, 'export-public-package.sh');

function writeFixtureFile(root, relativePath, contents = 'synthetic public package fixture\n') {
  const file = path.join(root, relativePath);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, contents);
}

function createExporterFixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-public-export-source-'));
  for (const file of [
    'README.md',
    'LICENSE',
    'CONTRIBUTING.md',
    '.gitignore',
    '.editorconfig',
    '.gitattributes',
    'worktree-ownership.yml',
    'SECURITY.md',
    'CHANGELOG.md',
    'THIRD_PARTY_NOTICES.md',
    'version.props',
    'Directory.Build.props',
    '.dockerignore',
    'gradlew',
    'gradle.properties',
    'build.gradle.kts',
    'settings.gradle.kts',
  ]) {
    fs.copyFileSync(path.join(repositoryRoot, file), path.join(root, file));
  }
  for (const directory of [
    'apps/mobile-shared',
    'apps/android',
    'apps/ios',
    'gradle',
    'modules/domain',
    'contracts',
    'platform',
    'tests',
    'tooling',
    'examples',
    '.github',
    'docs/api',
    'docs/adr',
    'docs/operations',
  ]) {
    fs.mkdirSync(path.join(root, directory), { recursive: true });
  }
  fs.cpSync(path.join(repositoryRoot, 'infra'), path.join(root, 'infra'), { recursive: true });
  fs.mkdirSync(path.join(root, 'scripts'), { recursive: true });
  for (const file of ['check-public-package.sh', 'check-deployment.sh', 'export-public-package.sh', 'sanitize-public-markdown-links.mjs', 'validate-audio-assets.mjs']) {
    fs.copyFileSync(path.join(repositoryRoot, 'scripts', file), path.join(root, 'scripts', file));
  }
  writeFixtureFile(root, 'modules/domain/README.md');
  writeFixtureFile(root, 'tests/architecture/README.md');
  writeFixtureFile(root, 'tooling/lint/README.md');
  writeFixtureFile(root, 'examples/README.md');
  writeFixtureFile(root, 'docs/api/README.md');
  writeFixtureFile(root, 'docs/adr/README.md');
  writeFixtureFile(root, 'docs/operations/README.md');
  writeFixtureFile(root, '.github/workflows/verify.yml');
  writeFixtureFile(root, 'gradle/libs.versions.toml');
  writeFixtureFile(root, 'apps/ios/Configuration/Config.xcconfig', '// safe checked-in baseline\n');
  writeFixtureFile(root, 'apps/ios/Configuration/Debug.xcconfig.example', '// safe example\n');
  writeFixtureFile(root, 'apps/ios/Configuration/Local.xcconfig', 'SUPABASE_URL=local-only\n');
  writeFixtureFile(root, 'apps/ios/Configuration/Debug.xcconfig', 'SUPABASE_URL=local-only\n');
    writeFixtureFile(root, 'apps/android/google-services.json', '{}\n');
  const databasePasswordName = ['DATABASE', 'PASSWORD'].join('_');
  writeFixtureFile(root, 'platform/api/.env.local', `${databasePasswordName}=local-only\n`);

  const audioRoot = path.join(root, 'apps', 'mobile-shared', 'src', 'commonMain', 'composeResources', 'files', 'audio');
  const audioFile = 'narration/onboarding.m4a';
  const audioBytes = Buffer.from('synthetic-reviewed-audio-fixture\n');
  const sourceText = 'Start with the evidence.';
  fs.mkdirSync(path.join(audioRoot, 'narration'), { recursive: true });
  fs.writeFileSync(path.join(audioRoot, audioFile), audioBytes);
  writeFixtureFile(root, 'docs/licenses/audio-assets.md', '# Synthetic test fixture license\n');
  fs.writeFileSync(
    path.join(audioRoot, 'audio-manifest.json'),
    JSON.stringify(
      {
        schema: 'evidrilo.audio-manifest',
        version: '1',
        entries: [
          {
            id: 'ONBOARDING',
            locale: 'en-US',
            sourceText,
            sourceTextSha256: crypto.createHash('sha256').update(sourceText, 'utf8').digest('hex'),
            file: audioFile,
            durationMs: 1000,
            byteLength: audioBytes.length,
            format: 'm4a',
            voiceLabel: 'synthetic fixture voice',
            licenseReference: 'docs/licenses/audio-assets.md',
          },
        ],
      },
      null,
      2,
    ),
  );
  fs.mkdirSync(path.join(root, 'internal', 'design'), { recursive: true });
  writeFixtureFile(root, 'internal/design/mockup.png', 'private design fixture\n');
  return root;
}

function collectMarkdownFiles(root) {
  const files = [];
  function walk(directory) {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const file = path.join(directory, entry.name);
      if (entry.isDirectory()) walk(file);
      else if (entry.isFile() && entry.name.endsWith('.md')) files.push(file);
    }
  }
  walk(root);
  return files;
}

function assertPublicRelativeLinksResolve(root) {
  const missing = [];
  for (const file of collectMarkdownFiles(root)) {
    const source = fs.readFileSync(file, 'utf8');
    for (const match of source.matchAll(/(?<!\!)\[([^\]]+)\]\(([^)\n]+)\)/g)) {
      const target = match[2].trim().replace(/^<|>$/g, '').split(/[?#]/, 1)[0];
      if (!target || target.startsWith('/') || target.startsWith('#') || /^[a-z][a-z0-9+.-]*:/i.test(target)) continue;
      const resolved = path.resolve(path.dirname(file), decodeURIComponent(target));
      if ((resolved !== root && !resolved.startsWith(`${root}${path.sep}`)) || !fs.existsSync(resolved)) {
        missing.push(`${path.relative(root, file)} -> ${target}`);
      }
    }
  }
  assert.deepEqual(missing, [], 'public Markdown contains unresolved relative links');
}

test('keeps public export closed until reviewed audio assets exist', () => {
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-public-export-'));
  const destination = path.join(temporaryRoot, 'candidate');
  try {
    const result = spawnSync('bash', [exporter, repositoryRoot, destination], { encoding: 'utf8' });
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /AUDIO_ASSETS_FAIL|reviewed assets/i);
    assert.equal(fs.existsSync(destination), false);
  } finally {
    fs.rmSync(temporaryRoot, { recursive: true, force: true });
  }
});

test('refuses to export over a non-empty destination', () => {
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-public-export-'));
  const destination = path.join(temporaryRoot, 'candidate');
  fs.mkdirSync(destination);
  fs.writeFileSync(path.join(destination, 'keep.txt'), 'do not overwrite\n');
  try {
    const result = spawnSync('bash', [exporter, repositoryRoot, destination], { encoding: 'utf8' });
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /non-empty|refus/i);
    assert.equal(fs.readFileSync(path.join(destination, 'keep.txt'), 'utf8'), 'do not overwrite\n');
  } finally {
    fs.rmSync(temporaryRoot, { recursive: true, force: true });
  }
});

test('does not export local Apple or provider configuration files', () => {
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-public-export-'));
  const source = createExporterFixture();
  const destination = path.join(temporaryRoot, 'candidate');
  try {
    const result = spawnSync('bash', [exporter, source, destination], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.existsSync(path.join(destination, 'apps/ios/Configuration/Config.xcconfig')), true);
    assert.equal(fs.existsSync(path.join(destination, 'apps/ios/Configuration/Debug.xcconfig.example')), true);
    for (const forbiddenPath of [
      'apps/ios/Configuration/Local.xcconfig',
      'apps/ios/Configuration/Debug.xcconfig',
      'apps/android/google-services.json',
      'platform/api/.env.local',
      'internal/design/mockup.png',
    ]) {
      assert.equal(fs.existsSync(path.join(destination, forbiddenPath)), false, forbiddenPath);
    }
  } finally {
    fs.rmSync(source, { recursive: true, force: true });
    fs.rmSync(temporaryRoot, { recursive: true, force: true });
  }
});
