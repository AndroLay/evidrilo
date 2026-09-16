import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const scriptsDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.dirname(scriptsDirectory);
const checker = path.join(scriptsDirectory, 'check-deployment.sh');

function runChecker(root) {
  return spawnSync('bash', [checker, root], { encoding: 'utf8' });
}

function createFixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-deployment-'));
  fs.mkdirSync(path.join(root, 'deploy', 'docker'), { recursive: true });
  fs.writeFileSync(path.join(root, '.dockerignore'), 'local.properties\n*.env\n.tmp\n');
  fs.writeFileSync(path.join(root, 'deploy', 'README.md'), 'deployment\n');
  fs.writeFileSync(path.join(root, 'deploy', 'docker', 'api.Dockerfile'), 'FROM mcr.microsoft.com/dotnet/sdk:10.0\n');
  fs.writeFileSync(path.join(root, 'deploy', 'docker', 'worker.Dockerfile'), 'FROM mcr.microsoft.com/dotnet/sdk:10.0\n');
  fs.writeFileSync(
    path.join(root, 'deploy', 'docker-compose.local.yml'),
    'services:\n  postgres:\n    healthcheck: {}\n  auth-shim:\n    volumes:\n      - platform/database/integration/auth-shim.sql:/auth-shim.sql\n  migrations:\n    depends_on:\n      auth-shim:\n        condition: service_completed_successfully\n  api:\n    depends_on: {}\n  worker:\n    depends_on: {}\n',
  );
  return root;
}

test('accepts the repository-owned deployment preparation', () => {
  const result = runChecker(repositoryRoot);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /DEPLOYMENT_CHECK_PASS/);
});

test('rejects a deployment fixture containing a secret-shaped assignment', () => {
  const root = createFixture();
  const secret = 'synthetic-secret-value-that-must-not-appear';
  try {
    const credentialName = ['DATABASE', 'PASSWORD'].join('_');
    fs.writeFileSync(path.join(root, 'deploy', 'secret.env'), `${credentialName}=${secret}\n`);
    const result = runChecker(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /credential|secret|password/i);
    assert.doesNotMatch(`${result.stdout}${result.stderr}`, new RegExp(secret));
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('rejects deployment preparation without a healthcheck', () => {
  const root = createFixture();
  try {
    fs.writeFileSync(
      path.join(root, 'deploy', 'docker-compose.local.yml'),
      'services:\n  postgres:\n    image: postgres:16-alpine\n',
    );
    const result = runChecker(root);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /healthcheck/i);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('requires the local Supabase Auth compatibility bootstrap', () => {
  const compose = fs.readFileSync(path.join(repositoryRoot, 'deploy', 'docker-compose.local.yml'), 'utf8');
  assert.match(compose, /auth-shim:/);
  assert.match(compose, /platform\/database\/integration\/auth-shim\.sql/);
  assert.match(compose, /auth-shim:\s*\n\s*condition: service_completed_successfully/s);
});

test('allows a local API port override for an occupied host port', () => {
  const compose = fs.readFileSync(path.join(repositoryRoot, 'deploy', 'docker-compose.local.yml'), 'utf8');
  assert.match(compose, /\$\{EVIDRILO_API_PORT:-5080\}:5080/);
});
