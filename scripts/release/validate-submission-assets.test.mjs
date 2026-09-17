import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const scriptsDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptsDirectory, '..', '..');
const validator = path.join(scriptsDirectory, 'validate-submission-assets.sh');
const currentIcon = path.join(
  repositoryRoot,
  'apps',
  'ios',
  'iosApp',
  'Assets.xcassets',
  'AppIcon.appiconset',
  'AppIcon-1024.png',
);

function runValidator(args = []) {
  return spawnSync('bash', [validator, repositoryRoot, ...args], { encoding: 'utf8' });
}

test('reports the existing icon and keeps missing final assets open', () => {
  const result = runValidator(['--icon', currentIcon]);
  assert.notEqual(result.status, 0);
  assert.match(`${result.stdout}${result.stderr}`, /ICON: PASS.*1024x1024/s);
  assert.match(`${result.stdout}${result.stderr}`, /SCREENSHOT: NOT_READY/);
  assert.match(`${result.stdout}${result.stderr}`, /VIDEO: NOT_READY/);
});

test('rejects an icon with the wrong dimensions', () => {
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-assets-'));
  const wrongIcon = path.join(temporaryRoot, 'wrong-icon.png');
  try {
    // PNG signature plus an IHDR declaring a 1x1 image is enough for the
    // validator's dimension check; the file is intentionally not a renderable
    // submission asset.
    const pngHeader = Buffer.from([
      0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
      0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
      0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    ]);
    fs.writeFileSync(wrongIcon, pngHeader);
    const result = runValidator(['--icon', wrongIcon]);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /ICON: FAIL.*1024x1024/s);
  } finally {
    fs.rmSync(temporaryRoot, { recursive: true, force: true });
  }
});
