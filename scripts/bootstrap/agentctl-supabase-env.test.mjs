import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const scriptsDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptsDirectory, '..', '..');
const helperPath = path.join(scriptsDirectory, 'agentctl-supabase-env.sh');
const apiBootstrapPath = path.join(scriptsDirectory, 'local-api-with-agentctl.sh');
const androidBootstrapPath = path.join(scriptsDirectory, 'build-android-local-with-agentctl.sh');

test('agentctl bootstrap scripts exist and fail closed on missing dependencies', () => {
  for (const filePath of [helperPath, apiBootstrapPath, androidBootstrapPath]) {
    assert.equal(fs.existsSync(filePath), true, filePath);
    const source = fs.readFileSync(filePath, 'utf8');
    assert.match(source, /set -Eeuo pipefail/);
    assert.doesNotMatch(source, /set -x/);
    assert.doesNotMatch(source, /SUPABASE_SERVICE_ROLE_KEY/);
  }
});

test('Supabase values are obtained through agentctl and not persisted by the bootstrap', () => {
  const helper = fs.readFileSync(helperPath, 'utf8');
  const apiBootstrap = fs.readFileSync(apiBootstrapPath, 'utf8');
  const androidBootstrap = fs.readFileSync(androidBootstrapPath, 'utf8');
  const source = `${helper}\n${apiBootstrap}\n${androidBootstrap}`;

  assert.match(source, /agentctl supabase project-list/);
  assert.match(source, /agentctl api request supabase GET/);
  assert.match(source, /secret-tool lookup service agentctl provider supabase profile default/);
  assert.match(source, /api\.supabase\.com\/v1\/projects/);
  assert.match(source, /SUPABASE_URL=/);
  assert.match(source, /SUPABASE_PUBLISHABLE_KEY=/);
  assert.match(androidBootstrap, /adb_path=.*\n\s+"\$adb_path" reverse/);
  assert.match(androidBootstrap, /android_api_host=\$\{EVIDRILO_ANDROID_API_HOST:-127\.0\.0\.1\}/);
  assert.doesNotMatch(source, /(?:tee|cat|printf|echo)\s+[^\n]*\.env/);
  assert.doesNotMatch(source, /echo\s+.*publishable/);
  assert.doesNotMatch(source, /printf\s+.*publishable/);
  assert.doesNotMatch(source, /\[redacted\].*process\.stdout/);
});

test('local compose exposure is loopback-only', () => {
  const compose = fs.readFileSync(
    path.join(repositoryRoot, 'infra', 'environments', 'local', 'docker-compose.yml'),
    'utf8',
  );
  assert.match(compose, /127\.0\.0\.1:\$\{EVIDRILO_API_PORT:-5080\}:5080/);
});

test('local API bootstrap preserves checkout-specific port and AI override', () => {
  const apiBootstrap = fs.readFileSync(apiBootstrapPath, 'utf8');
  assert.match(apiBootstrap, /\.local\/api-port/);
  assert.match(apiBootstrap, /\.local\/docker-compose\.ai\.yml/);
  assert.match(apiBootstrap, /docker compose "\$\{compose_files\[@\]\}"/);
  assert.match(fs.readFileSync(path.join(repositoryRoot, '.gitignore'), 'utf8'), /^\.local\/$/m);
});
