import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = path.dirname(fileURLToPath(import.meta.url));
const checker = path.join(scriptsDirectory, 'check-public-package.sh');

function createCandidate() {
  const candidate = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-public-package-'));
  for (const directory of ['composeApp', 'androidApp', 'iosApp', 'contracts', 'platform', 'scripts']) {
    fs.mkdirSync(path.join(candidate, directory));
  }
  for (const file of ['README.md', 'LICENSE', 'CONTRIBUTING.md', '.gitignore', 'worktree-ownership.yml', 'gradlew', 'settings.gradle.kts']) {
    fs.writeFileSync(path.join(candidate, file), 'synthetic public package fixture\n');
  }
  return candidate;
}

function runChecker(candidate) {
  return spawnSync('bash', [checker, candidate], { encoding: 'utf8' });
}

test('accepts a minimal clean public package', () => {
  const candidate = createCandidate();
  try {
    const result = runChecker(candidate);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /PUBLIC_PACKAGE_CHECK_PASS/);
  } finally {
    fs.rmSync(candidate, { recursive: true, force: true });
  }
});

test('rejects private media directories', () => {
  const candidate = createCandidate();
  try {
    fs.mkdirSync(path.join(candidate, 'Gurwi'));
    const result = runChecker(candidate);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /private|forbidden|Gurwi/i);
  } finally {
    fs.rmSync(candidate, { recursive: true, force: true });
  }
});

test('rejects private design directories and historical architecture notes', () => {
  const candidate = createCandidate();
  try {
    fs.mkdirSync(path.join(candidate, 'design'));
    fs.mkdirSync(path.join(candidate, 'docs', 'architecture'), { recursive: true });
    fs.writeFileSync(path.join(candidate, 'docs', 'architecture', 'revenuecat-integration.md'), 'historical\n');
    const result = runChecker(candidate);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /private|forbidden|design|historical/i);
  } finally {
    fs.rmSync(candidate, { recursive: true, force: true });
  }
});

test('rejects the private internal design directory', () => {
  const candidate = createCandidate();
  try {
    fs.mkdirSync(path.join(candidate, 'internal', 'design'), { recursive: true });
    const result = runChecker(candidate);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /private|forbidden|internal|design/i);
  } finally {
    fs.rmSync(candidate, { recursive: true, force: true });
  }
});

test('rejects the private narration source inventory', () => {
  const candidate = createCandidate();
  try {
    fs.mkdirSync(path.join(candidate, 'docs', 'licenses'), { recursive: true });
    fs.writeFileSync(
      path.join(candidate, 'docs', 'licenses', 'audio-narration-source-inventory.md'),
      'private narration handoff\n',
    );
    const result = runChecker(candidate);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /private|forbidden|narration|inventory/i);
  } finally {
    fs.rmSync(candidate, { recursive: true, force: true });
  }
});

test('rejects an incomplete public package', () => {
  const candidate = createCandidate();
  try {
    fs.rmSync(path.join(candidate, 'iosApp'), { recursive: true, force: true });
    const result = runChecker(candidate);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /missing required public path.*iosApp/i);
  } finally {
    fs.rmSync(candidate, { recursive: true, force: true });
  }
});

test('rejects credential-shaped values without echoing their contents', () => {
  const candidate = createCandidate();
  const syntheticSecret = 'synthetic-service-role-value-that-must-not-appear';
  try {
    fs.writeFileSync(path.join(candidate, 'local-config.txt'), `service_role=${syntheticSecret}\n`);
    const result = runChecker(candidate);
    assert.notEqual(result.status, 0);
    assert.doesNotMatch(`${result.stdout}${result.stderr}`, new RegExp(syntheticSecret));
  } finally {
    fs.rmSync(candidate, { recursive: true, force: true });
  }
});

test('rejects credential-bearing filenames', () => {
  const candidate = createCandidate();
  try {
    fs.writeFileSync(path.join(candidate, 'google-services.json'), '{}\n');
    const result = runChecker(candidate);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /credential-bearing file|google-services/i);
  } finally {
    fs.rmSync(candidate, { recursive: true, force: true });
  }
});

test('rejects generated root temporary output', () => {
  const candidate = createCandidate();
  try {
    fs.mkdirSync(path.join(candidate, '.tmp'), { recursive: true });
    fs.writeFileSync(path.join(candidate, '.tmp', 'compiler-report.json'), 'generated output\n');
    const result = runChecker(candidate);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /forbidden|temporary|\.tmp/i);
  } finally {
    fs.rmSync(candidate, { recursive: true, force: true });
  }
});
