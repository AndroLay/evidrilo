import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.dirname(scriptsDirectory);

function read(relativePath) {
  return fs.readFileSync(path.join(repositoryRoot, relativePath), 'utf8');
}

test('Android host declares network access for authenticated app services', () => {
  const manifest = read('apps/android/src/main/AndroidManifest.xml');
  assert.match(
    manifest,
    /<uses-permission\s+android:name="android\.permission\.INTERNET"\s*\/>/,
  );
});

test('Android host registers only the Evidrilo auth callback route', () => {
  const manifest = read('apps/android/src/main/AndroidManifest.xml');
  assert.match(manifest, /android:scheme="evidrilo"/);
  assert.match(manifest, /android:host="auth"/);
  assert.match(manifest, /android:path="\/callback"/);
});

test('iOS host registers the same auth callback scheme', () => {
  const plist = read('iosApp/iosApp/Info.plist');
  assert.match(plist, /<key>CFBundleURLTypes<\/key>/);
  assert.match(plist, /<string>evidrilo<\/string>/);
  assert.match(read('iosApp/iosApp/iosApp.swift'), /submitAccountAuthRedirect/);
});

test('Android release configuration is optimized and signing-safe', () => {
  const gradle = read('apps/android/build.gradle.kts');
  assert.match(gradle, /buildTypes\s*\{[\s\S]*release\s*\{[\s\S]*isMinifyEnabled\s*=\s*true/);
  assert.match(gradle, /release\s*\{[\s\S]*isShrinkResources\s*=\s*true/);
  assert.match(gradle, /proguard-android-optimize\.txt/);
  assert.match(gradle, /verifyReleaseSigning/);
  assert.doesNotMatch(gradle, /release\s*\{[\s\S]*signingConfig\s*=\s*signingConfigs\.getByName\("debug"\)/);
});

test('Android release disables backup and cleartext transport', () => {
  const manifest = read('apps/android/src/main/AndroidManifest.xml');
  assert.match(manifest, /android:allowBackup="false"/);
  assert.match(manifest, /android:usesCleartextTraffic="false"/);
});

test('iOS Release uses distribution signing with an owner-supplied team', () => {
  const project = read('iosApp/iosApp.xcodeproj/project.pbxproj');
  const releaseTarget = project.slice(project.indexOf('7555FFA7242A565B00829871 /* Release */'));
  assert.match(releaseTarget, /CODE_SIGN_IDENTITY = "Apple Distribution";/);
  assert.match(releaseTarget, /DEVELOPMENT_TEAM = "\$\(TEAM_ID\)";/);
  assert.doesNotMatch(releaseTarget, /CODE_SIGN_IDENTITY = "Apple Development";/);
  assert.doesNotMatch(releaseTarget, /DEVELOPMENT_ASSET_PATHS/);
  assert.match(read('iosApp/Configuration/Release.xcconfig.example'), /TEAM_ID =/);
});

test('mobile release configuration checker passes without exposing signing material', () => {
  const checker = path.join(scriptsDirectory, 'check-mobile-release.sh');
  const result = spawnSync('bash', [checker, repositoryRoot], { encoding: 'utf8' });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /MOBILE_RELEASE_CONFIG_PASS/);
  assert.match(result.stdout, /ANDROID_RELEASE_SIGNING: EXTERNAL/);
  assert.match(result.stdout, /IOS_RELEASE_SIGNING: EXTERNAL/);
  assert.doesNotMatch(`${result.stdout}${result.stderr}`, /password|secret|token/i);
});

test('mobile release checker accepts Android artifact and iOS archive together', () => {
  const checker = path.join(scriptsDirectory, 'check-mobile-release.sh');
  const archive = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-archive-'));
  fs.writeFileSync(path.join(archive, 'Info.plist'), 'synthetic archive metadata');
  try {
    const result = spawnSync(
      'bash',
      [checker, repositoryRoot, '--require-android-artifact', '--require-ios-archive', archive],
      { encoding: 'utf8' },
    );
    assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
    assert.match(result.stdout, /ANDROID_RELEASE_ARTIFACT: PASS/);
    assert.match(result.stdout, /IOS_RELEASE_ARCHIVE: PASS/);
  } finally {
    fs.rmSync(archive, { recursive: true, force: true });
  }
});

test('CI exposes Android release and unsigned iOS host lanes', () => {
  const workflow = read('.github/workflows/verify.yml');
  assert.match(workflow, /:androidApp:bundleRelease/);
  assert.match(workflow, /runs-on: macos-14/);
  assert.match(workflow, /xcodebuild/);
  assert.match(workflow, /CODE_SIGNING_ALLOWED=NO/);
  assert.match(workflow, /-scheme Evidrilo/);
});
