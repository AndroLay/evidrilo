import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptsDirectory, '..', '..');

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
  const plist = read('apps/ios/iosApp/Info.plist');
  assert.match(plist, /<key>CFBundleURLTypes<\/key>/);
  assert.match(plist, /<string>evidrilo<\/string>/);
  assert.match(read('apps/ios/iosApp/iosApp.swift'), /submitAccountAuthRedirect/);
});

test('iOS host build phase invokes the repository Gradle wrapper', () => {
  const project = read('apps/ios/iosApp.xcodeproj/project.pbxproj');
  const buildPhaseStart = project.indexOf('name = "Compile Kotlin Framework";');
  assert.notEqual(buildPhaseStart, -1, 'iOS Kotlin framework build phase is missing');
  const buildPhase = project.slice(buildPhaseStart, project.indexOf('/* End PBXShellScriptBuildPhase section */', buildPhaseStart));
  assert.match(buildPhase, /cd \\\"\$SRCROOT\/\.\.\/\.\.\\"/);
  assert.match(buildPhase, /\.\/gradlew :composeApp:embedAndSignAppleFrameworkForXcode/);
  assert.equal(fs.existsSync(path.join(repositoryRoot, 'gradlew')), true);
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

test('local reminder platform boundary is wired without remote-push dependencies', () => {
  const manifest = read('apps/android/src/main/AndroidManifest.xml');
  assert.match(manifest, /android\.permission\.POST_NOTIFICATIONS/);
  assert.match(manifest, /EvidriloLocalNotificationReceiver/);
  const activity = read('apps/android/src/main/kotlin/dev/nextgen/mobile/android/MainActivity.kt');
  assert.match(activity, /AndroidNotificationPreferencesStorage\.initialize/);
  assert.match(activity, /AndroidLocalNotificationPlatform\.initialize/);
  assert.match(activity, /onRequestPermissionsResult/);
  assert.match(
    read('modules/application/src/commonMain/kotlin/dev/nextgen/mobile/notifications/NotificationModels.kt'),
    /expect fun createLocalNotificationScheduler\(\)/,
  );
  assert.match(
    read('modules/application/src/androidMain/kotlin/dev/nextgen/mobile/notifications/LocalNotificationScheduler.android.kt'),
    /setInexactRepeating/,
  );
  assert.match(
    read('modules/application/src/iosMain/kotlin/dev/nextgen/mobile/notifications/LocalNotificationScheduler.ios.kt'),
    /UNUserNotificationCenter/,
  );
  assert.doesNotMatch(
    read('modules/application/src/commonMain/kotlin/dev/nextgen/mobile/notifications/NotificationModels.kt'),
    /OneSignal|FCM|remote push/i,
  );
});

test('iOS Release uses distribution signing with an owner-supplied team', () => {
  const project = read('apps/ios/iosApp.xcodeproj/project.pbxproj');
  const releaseTarget = project.slice(project.indexOf('7555FFA7242A565B00829871 /* Release */'));
  assert.match(releaseTarget, /CODE_SIGN_IDENTITY = "Apple Distribution";/);
  assert.match(releaseTarget, /DEVELOPMENT_TEAM = "\$\(TEAM_ID\)";/);
  assert.doesNotMatch(releaseTarget, /CODE_SIGN_IDENTITY = "Apple Development";/);
  assert.doesNotMatch(releaseTarget, /DEVELOPMENT_ASSET_PATHS/);
  assert.match(read('apps/ios/Configuration/Release.xcconfig.example'), /TEAM_ID =/);
});

test('mobile release configuration checker passes without exposing signing material', () => {
  const checker = path.join(repositoryRoot, 'scripts', 'release', 'check-mobile-release.sh');
  const result = spawnSync('bash', [checker, repositoryRoot], { encoding: 'utf8' });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /MOBILE_RELEASE_CONFIG_PASS/);
  assert.match(result.stdout, /ANDROID_RELEASE_SIGNING: EXTERNAL/);
  assert.match(result.stdout, /IOS_RELEASE_SIGNING: EXTERNAL/);
  assert.doesNotMatch(`${result.stdout}${result.stderr}`, /password|secret|token/i);
});

test('mobile release checker accepts Android artifact and iOS archive together', () => {
  const checker = path.join(repositoryRoot, 'scripts', 'release', 'check-mobile-release.sh');
  const fixtureRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-mobile-release-'));
  const archive = fs.mkdtempSync(path.join(os.tmpdir(), 'evidrilo-archive-'));
  const androidArtifact = path.join(fixtureRoot, 'androidApp-release.aab');
  fs.writeFileSync(androidArtifact, 'synthetic release artifact fixture\n');
  fs.writeFileSync(path.join(archive, 'Info.plist'), 'synthetic archive metadata');
  try {
    const result = spawnSync(
      'bash',
      [
        checker,
        repositoryRoot,
        '--require-android-artifact',
        '--android-artifact',
        androidArtifact,
        '--require-ios-archive',
        archive,
      ],
      { encoding: 'utf8' },
    );
    assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
    assert.match(result.stdout, /ANDROID_RELEASE_ARTIFACT: PASS/);
    assert.match(result.stdout, /IOS_RELEASE_ARCHIVE: PASS/);
  } finally {
    fs.rmSync(fixtureRoot, { recursive: true, force: true });
    fs.rmSync(archive, { recursive: true, force: true });
  }
});

test('CI exposes Android release and a separate unsigned iOS host lane', () => {
  const workflow = read('.github/workflows/verify.yml');
  const iosWorkflow = read('.github/workflows/ios-simulator.yml');
  assert.match(workflow, /:androidApp:bundleRelease/);
  assert.match(iosWorkflow, /runs-on: macos-15/);
  assert.match(iosWorkflow, /xcodebuild/);
  assert.match(iosWorkflow, /CODE_SIGNING_ALLOWED=NO/);
  assert.match(iosWorkflow, /-scheme Evidrilo/);
  assert.match(iosWorkflow, /workflow_dispatch/);
  assert.match(iosWorkflow, /evidrilo-ios-build/);
});

test('CI runs an iOS simulator smoke test and uploads evidence', () => {
  const workflow = read('.github/workflows/ios-simulator.yml');
  const smokeScriptPath = path.join(repositoryRoot, 'scripts', 'ios', 'smoke-simulator.sh');
  assert.equal(fs.existsSync(smokeScriptPath), true, 'iOS simulator smoke script is missing');
  const smokeScript = fs.readFileSync(smokeScriptPath, 'utf8');
  assert.match(workflow, /scripts\/ios\/smoke-simulator\.sh/);
  assert.match(workflow, /actions\/upload-artifact@v4/);
  assert.match(workflow, /ios-simulator-evidence/);
  assert.match(smokeScript, /xcrun simctl bootstatus/);
  assert.match(smokeScript, /xcrun simctl install/);
  assert.match(smokeScript, /xcrun simctl launch/);
  assert.match(smokeScript, /xcrun simctl io/);
});
