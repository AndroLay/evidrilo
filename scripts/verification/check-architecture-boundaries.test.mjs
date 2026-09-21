import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { tmpdir } from 'node:os';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const checker = path.join(repositoryRoot, 'scripts', 'verification', 'check-architecture-boundaries.sh');

test('current staged architecture satisfies the domain and feature dependency guard', () => {
  const result = spawnSync('bash', [checker, repositoryRoot], { encoding: 'utf8' });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /ARCHITECTURE_BOUNDARY_PASS/);
});

function runChecker(root, env = process.env) {
  return spawnSync('/bin/bash', [checker, root], {
    encoding: 'utf8',
    env,
  });
}

function withFixture(relativeFile, source, callback) {
  const root = mkdtempSync(path.join(tmpdir(), 'evidrilo-architecture-'));
  const file = path.join(root, relativeFile);
  mkdirSync(path.dirname(file), { recursive: true });
  writeFileSync(file, source, 'utf8');
  try {
    return callback(root);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

test('forbidden imports fail closed across every protected layer', () => {
  const fixtures = [
    ['modules/domain/src/commonMain/kotlin/Forbidden.kt', 'import android.app.Application\n'],
    ['modules/domain/src/commonMain/kotlin/Forbidden.kt', 'import com.revenuecat.purchases.CustomerInfo\n'],
    ['modules/domain/src/commonMain/kotlin/Forbidden.kt', 'import dev.nextgen.mobile.storage.LocalStorage\n'],
    ['modules/core/src/commonMain/Forbidden.kt', 'import dev.nextgen.mobile.domain.conclusion.ConclusionCase\n'],
    ['modules/application/src/commonMain/Forbidden.kt', 'import androidx.compose.runtime.Composable\n'],
    ['modules/application/src/commonMain/Forbidden.kt', 'import dev.nextgen.mobile.billing.BillingGateway\n'],
    ['modules/features/src/commonMain/Forbidden.kt', 'import com.revenuecat.purchases.CustomerInfo\n'],
    ['apps/mobile-shared/src/commonMain/kotlin/dev/nextgen/mobile/surfaces/Forbidden.kt', 'import io.ktor.client.HttpClient\n'],
    ['modules/design-system/src/commonMain/Forbidden.kt', 'import java.sql.Connection\n'],
  ];

  for (const [relativeFile, source] of fixtures) {
    withFixture(relativeFile, source, (root) => {
      const result = runChecker(root);
      assert.notEqual(result.status, 0, `${relativeFile} unexpectedly passed`);
      assert.match(`${result.stdout}${result.stderr}`, /ARCHITECTURE_BOUNDARY_/);
    });
  }
});

test('a valid protected-layer fixture passes', () => {
  withFixture(
    'modules/domain/src/commonMain/kotlin/Valid.kt',
    'package fixture\n\nimport kotlin.collections.List\n',
    (root) => {
      const result = runChecker(root);
      assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
      assert.match(result.stdout, /ARCHITECTURE_BOUNDARY_PASS/);
    },
  );
});

test('scanner unavailability fails closed', () => {
  withFixture(
    'modules/domain/src/commonMain/kotlin/Valid.kt',
    'package fixture\n',
    (root) => {
      const emptyPath = mkdtempSync(path.join(tmpdir(), 'evidrilo-no-rg-'));
      try {
        const result = runChecker(root, { ...process.env, PATH: emptyPath });
        assert.notEqual(result.status, 0);
        assert.match(`${result.stdout}${result.stderr}`, /ARCHITECTURE_BOUNDARY_SCAN_UNAVAILABLE/);
      } finally {
        rmSync(emptyPath, { recursive: true, force: true });
      }
    },
  );
});
