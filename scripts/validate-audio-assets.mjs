#!/usr/bin/env node

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const NARRATION_BUDGET_BYTES = 8 * 1024 * 1024;
const EFFECTS_BUDGET_BYTES = 300 * 1024;
const SUPPORTED_FORMATS = new Set(['caf', 'm4a', 'wav']);
const REQUIRED_FIELDS = [
  'id',
  'locale',
  'sourceText',
  'sourceTextSha256',
  'file',
  'durationMs',
  'byteLength',
  'format',
  'voiceLabel',
  'licenseReference',
];

function usage() {
  process.stderr.write(
    'usage: node scripts/validate-audio-assets.mjs <repository-root> [--allow-empty]\n',
  );
}

function fail(errors) {
  process.stderr.write('AUDIO_ASSETS_FAIL\n');
  for (const error of errors) {
    process.stderr.write(`- ${error}\n`);
  }
  process.exitCode = 1;
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isSafeRelativePath(value) {
  if (typeof value !== 'string' || value.length === 0 || value.includes('\\')) {
    return false;
  }
  if (path.posix.isAbsolute(value)) {
    return false;
  }
  const normalized = path.posix.normalize(value);
  return normalized === value && normalized !== '.' && normalized !== '..' && !normalized.startsWith('../');
}

function resolveInside(base, relativePath) {
  if (!isSafeRelativePath(relativePath)) {
    return null;
  }
  const resolved = path.resolve(base, ...relativePath.split('/'));
  const prefix = `${path.resolve(base)}${path.sep}`;
  return resolved.startsWith(prefix) ? resolved : null;
}

function sha256Text(value) {
  return crypto.createHash('sha256').update(value, 'utf8').digest('hex');
}

function listFiles(directory, prefix = '') {
  const files = [];
  for (const item of fs.readdirSync(directory, { withFileTypes: true })) {
    const relativePath = prefix ? `${prefix}/${item.name}` : item.name;
    const absolutePath = path.join(directory, item.name);
    if (item.isDirectory()) {
      files.push(...listFiles(absolutePath, relativePath));
    } else if (item.isFile() || item.isSymbolicLink()) {
      files.push({ absolutePath, relativePath, symbolic: item.isSymbolicLink() });
    }
  }
  return files;
}

function main() {
  const args = process.argv.slice(2);
  let repositoryRootArgument = null;
  let allowEmpty = false;
  for (const argument of args) {
    if (argument === '--allow-empty') {
      allowEmpty = true;
    } else if (repositoryRootArgument === null) {
      repositoryRootArgument = argument;
    } else {
      usage();
      process.exitCode = 2;
      return;
    }
  }

  if (repositoryRootArgument === null) {
    usage();
    process.exitCode = 2;
    return;
  }

  const repositoryRoot = path.resolve(repositoryRootArgument);
  if (!fs.existsSync(repositoryRoot) || !fs.statSync(repositoryRoot).isDirectory()) {
    fail(['repository root is not a directory']);
    return;
  }

  const audioRoot = path.join(
    repositoryRoot,
    'apps',
    'mobile-shared',
    'src',
    'commonMain',
    'composeResources',
    'files',
    'audio',
  );
  const manifestPath = path.join(audioRoot, 'audio-manifest.json');
  const errors = [];

  if (!fs.existsSync(audioRoot) || !fs.statSync(audioRoot).isDirectory()) {
    fail(['audio asset directory is missing']);
    return;
  }
  if (!fs.existsSync(manifestPath) || !fs.statSync(manifestPath).isFile()) {
    fail(['audio manifest is missing']);
    return;
  }

  let manifest;
  try {
    manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  } catch {
    fail(['audio manifest is not valid JSON']);
    return;
  }

  if (!isObject(manifest) || manifest.schema !== 'evidrilo.audio-manifest') {
    errors.push('audio manifest schema is invalid');
  }
  if (!isObject(manifest) || manifest.version !== '1') {
    errors.push('audio manifest version is unsupported');
  }
  if (!isObject(manifest) || !Array.isArray(manifest.entries)) {
    errors.push('audio manifest entries must be an array');
    fail(errors);
    return;
  }

  const entries = manifest.entries;
  if (entries.length === 0) {
    if (allowEmpty) {
      const files = listFiles(audioRoot).filter((file) => file.relativePath !== 'audio-manifest.json');
      if (files.length > 0) {
        errors.push(`orphan audio file count is ${files.length}`);
      }
    } else {
      errors.push('audio manifest is empty; reviewed assets are required');
    }
  }

  const ids = new Set();
  const referencedFiles = new Set();
  let narrationEntries = 0;
  let narrationBytes = 0;
  let effectsBytes = 0;

  entries.forEach((entry, index) => {
    const label = `entry #${index + 1}`;
    if (!isObject(entry)) {
      errors.push(`${label} must be an object`);
      return;
    }

    for (const field of REQUIRED_FIELDS) {
      if (!Object.prototype.hasOwnProperty.call(entry, field)) {
        errors.push(`${label} is missing ${field}`);
      }
    }

    if (typeof entry.id !== 'string' || !/^[A-Z][A-Z0-9_]*$/.test(entry.id)) {
      errors.push(`${label} has an invalid semantic ID`);
    } else if (ids.has(entry.id)) {
      errors.push(`duplicate semantic ID in ${label}`);
    } else {
      ids.add(entry.id);
    }

    if (typeof entry.locale !== 'string' || !/^[a-z]{2}(?:-[A-Z]{2})?$/.test(entry.locale)) {
      errors.push(`${label} has an invalid locale`);
    }

    if (typeof entry.sourceText !== 'string' || entry.sourceText.trim().length === 0) {
      errors.push(`${label} has empty source text`);
    } else if (
      typeof entry.sourceTextSha256 !== 'string' ||
      !/^[a-f0-9]{64}$/.test(entry.sourceTextSha256) ||
      sha256Text(entry.sourceText) !== entry.sourceTextSha256
    ) {
      errors.push(`${label} source-text checksum mismatch`);
    }

    const assetPath = resolveInside(audioRoot, entry.file);
    const fileIsKnown = typeof entry.file === 'string' && assetPath !== null;
    if (!fileIsKnown) {
      errors.push(`${label} has an unsafe asset path`);
    }

    let actualBytes = null;
    if (fileIsKnown) {
      const relativePath = path.posix.normalize(entry.file);
      if (!relativePath.startsWith('narration/') && !relativePath.startsWith('effects/')) {
        errors.push(`${label} must belong to narration/ or effects/`);
      }
      if (referencedFiles.has(relativePath)) {
        errors.push(`duplicate asset file in ${label}`);
      }
      referencedFiles.add(relativePath);

      try {
        const stats = fs.lstatSync(assetPath);
        if (stats.isSymbolicLink()) {
          errors.push(`${label} uses a symbolic-link asset`);
        } else if (!stats.isFile()) {
          errors.push(`${label} asset is not a regular file`);
        } else {
          actualBytes = stats.size;
        }
      } catch {
        errors.push(`${label} asset file is missing`);
      }
    }

    const format = typeof entry.format === 'string' ? entry.format.toLowerCase() : '';
    const extension = typeof entry.file === 'string' ? path.posix.extname(entry.file).slice(1).toLowerCase() : '';
    if (!SUPPORTED_FORMATS.has(format) || format !== extension) {
      errors.push(`${label} uses an unsupported or mismatched audio format`);
    }

    if (typeof entry.durationMs !== 'number' || !Number.isInteger(entry.durationMs) || entry.durationMs <= 0) {
      errors.push(`${label} has an invalid duration`);
    }
    if (typeof entry.byteLength !== 'number' || !Number.isInteger(entry.byteLength) || entry.byteLength < 0) {
      errors.push(`${label} has an invalid byteLength`);
    } else if (actualBytes !== null && entry.byteLength !== actualBytes) {
      errors.push(`${label} byteLength does not match the bundled file`);
    }
    if (typeof entry.voiceLabel !== 'string' || entry.voiceLabel.trim().length === 0) {
      errors.push(`${label} has no voice label`);
    }

    const licensePath = resolveInside(repositoryRoot, entry.licenseReference);
    if (
      typeof entry.licenseReference !== 'string' ||
      licensePath === null ||
      !entry.licenseReference.startsWith('docs/licenses/')
    ) {
      errors.push(`${label} has an unsafe license reference`);
    } else {
      try {
        if (!fs.statSync(licensePath).isFile()) {
          errors.push(`${label} license reference is not a file`);
        }
      } catch {
        errors.push(`${label} license reference is missing`);
      }
    }

    if (actualBytes !== null) {
      if (typeof entry.file === 'string' && entry.file.startsWith('narration/')) {
        narrationEntries += 1;
        narrationBytes += actualBytes;
      } else if (typeof entry.file === 'string' && entry.file.startsWith('effects/')) {
        effectsBytes += actualBytes;
      }
    }
  });

  const assetFiles = listFiles(audioRoot).filter((file) => file.relativePath !== 'audio-manifest.json');
  for (const assetFile of assetFiles) {
    if (!referencedFiles.has(assetFile.relativePath)) {
      errors.push(`orphan audio file: ${assetFile.relativePath}`);
    }
  }

  if (!allowEmpty && narrationEntries === 0) {
    errors.push('strict asset validation requires at least one reviewed narration asset');
  }

  if (narrationBytes > NARRATION_BUDGET_BYTES) {
    errors.push(`narration budget exceeded (${narrationBytes} bytes; maximum is 8 MB)`);
  }
  if (effectsBytes > EFFECTS_BUDGET_BYTES) {
    errors.push(`effects budget exceeded (${effectsBytes} bytes; maximum is 300 KB)`);
  }

  if (errors.length > 0) {
    fail(errors);
    return;
  }

  if (entries.length === 0) {
    process.stdout.write('AUDIO_ASSETS_EMPTY_ALLOWED\n');
  } else {
    process.stdout.write(
      `AUDIO_ASSETS_PASS (entries=${entries.length}; narrationBytes=${narrationBytes}; effectsBytes=${effectsBytes})\n`,
    );
  }
}

main();
