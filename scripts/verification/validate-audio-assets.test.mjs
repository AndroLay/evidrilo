import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const scriptsDirectory = path.dirname(fileURLToPath(import.meta.url));
const validator = path.join(scriptsDirectory, 'validate-audio-assets.mjs');

function hash(text) {
  return crypto.createHash('sha256').update(text, 'utf8').digest('hex');
}

function createFixture({ entries = [], files = {}, license = true } = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-audio-'));
  const audioRoot = path.join(
    root,
    'apps',
    'mobile-shared',
    'src',
    'commonMain',
    'composeResources',
    'files',
    'audio',
  );
  fs.mkdirSync(audioRoot, { recursive: true });
  for (const [relativePath, content] of Object.entries(files)) {
    const target = path.join(audioRoot, relativePath);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, content);
  }
  if (license) {
    fs.writeFileSync(path.join(root, 'THIRD_PARTY_NOTICES.md'), '# Third-party notices\n');
  }
  const manifestEntries = entries.map(({ bytes: _bytes, ...metadata }) => metadata);
  fs.writeFileSync(
    path.join(audioRoot, 'audio-manifest.json'),
    JSON.stringify({ schema: 'evidrilo.audio-manifest', version: '1', entries: manifestEntries }, null, 2),
  );
  return root;
}

function entry(overrides = {}) {
  const sourceText = overrides.sourceText ?? 'Start with the evidence.';
  const file = overrides.file ?? 'narration/onboarding.m4a';
  const bytes = overrides.bytes ?? Buffer.from('synthetic-audio-fixture');
  return {
    id: overrides.id ?? 'ONBOARDING',
    locale: overrides.locale ?? 'en-US',
    sourceText,
    sourceTextSha256: overrides.sourceTextSha256 ?? hash(sourceText),
    file,
    durationMs: overrides.durationMs ?? 1000,
    byteLength: overrides.byteLength ?? bytes.length,
    format: overrides.format ?? 'm4a',
    voiceLabel: overrides.voiceLabel ?? 'fixture voice',
    licenseReference: overrides.licenseReference ?? 'THIRD_PARTY_NOTICES.md',
    bytes,
  };
}

function runValidator(root, args = []) {
  return spawnSync(process.execPath, [validator, root, ...args], { encoding: 'utf8' });
}

function writeEntries(root, entries, files = {}) {
  const audioRoot = path.join(
    root,
    'apps',
    'mobile-shared',
    'src',
    'commonMain',
    'composeResources',
    'files',
    'audio',
  );
  for (const item of entries) {
    const target = path.join(audioRoot, item.file);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, item.bytes);
  }
  for (const [relativePath, content] of Object.entries(files)) {
    const target = path.join(audioRoot, relativePath);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, content);
  }
  const manifestEntries = entries.map(({ bytes: _bytes, ...metadata }) => metadata);
  fs.writeFileSync(
    path.join(audioRoot, 'audio-manifest.json'),
    JSON.stringify({ schema: 'evidrilo.audio-manifest', version: '1', entries: manifestEntries }, null, 2),
  );
}

test('accepts a valid manifest and matching bundled audio file', () => {
  const item = entry();
  const root = createFixture({ entries: [item], files: { [item.file]: item.bytes } });
  try {
    const result = runValidator(root);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /AUDIO_ASSETS_PASS/);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('allows an empty catalog only with the explicit implementation flag', () => {
  const root = createFixture();
  try {
    const strict = runValidator(root);
    assert.notEqual(strict.status, 0);
    assert.match(`${strict.stdout}${strict.stderr}`, /empty|asset/i);
    const allowed = runValidator(root, ['--allow-empty']);
    assert.equal(allowed.status, 0, allowed.stderr);
    assert.match(allowed.stdout, /AUDIO_ASSETS_EMPTY_ALLOWED/);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('strict mode requires at least one reviewed narration asset', () => {
  const item = entry({
    id: 'SELECTION',
    file: 'effects/selection.wav',
    format: 'wav',
    sourceText: 'Selection feedback cue.',
  });
  const root = createFixture({ entries: [item], files: { [item.file]: item.bytes } });
  try {
    const result = runValidator(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /requires.*narration.*asset/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('rejects a source-text checksum mismatch without echoing source data', () => {
  const item = entry({ sourceTextSha256: '0'.repeat(64) });
  const root = createFixture({ entries: [item], files: { [item.file]: item.bytes } });
  try {
    const result = runValidator(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /checksum/i);
    assert.doesNotMatch(`${result.stdout}${result.stderr}`, new RegExp(item.sourceText));
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('rejects duplicate IDs, orphan files, and missing asset files', () => {
  const first = entry();
  const second = entry({ sourceText: 'Another sentence.' });
  const root = createFixture({
    entries: [first, second],
    files: { [first.file]: first.bytes, 'effects/orphan.wav': Buffer.from('orphan') },
  });
  try {
    const duplicate = runValidator(root);
    assert.notEqual(duplicate.status, 0);
    assert.match(`${duplicate.stdout}${duplicate.stderr}`, /duplicate/i);

    writeEntries(root, [entry({ file: 'narration/missing.m4a' })], {
      'effects/orphan.wav': Buffer.from('orphan'),
    });
    const missing = runValidator(root);
    assert.notEqual(missing.status, 0);
    assert.match(`${missing.stdout}${missing.stderr}`, /missing|orphan/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('rejects missing license references and unsupported formats', () => {
  const item = entry({ format: 'mp3', licenseReference: 'docs/licenses/missing.md' });
  const root = createFixture({ entries: [item], files: { [item.file]: item.bytes }, license: true });
  try {
    const result = runValidator(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /license|format/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('rejects narration and effects that exceed their independent budgets', () => {
  const item = entry({ bytes: Buffer.alloc(8 * 1024 * 1024 + 1) });
  const root = createFixture({ entries: [item], files: { [item.file]: item.bytes } });
  try {
    const result = runValidator(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /budget|8 MB|size/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
