#!/usr/bin/env bash

set -Eeuo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"

# shellcheck source=scripts/bootstrap/toolchain-paths.sh
source "$repo_root/scripts/bootstrap/toolchain-paths.sh"
gradle_user_home=$(evidrilo_gradle_user_home)
# Gradle Wrapper uses this variable to locate its distribution before it can
# process the --gradle-user-home command-line option.
export GRADLE_USER_HOME="$gradle_user_home"

printf '%s\n' 'Evidrilo local verification'
is_jdk21_home() {
    local candidate=$1
    [ -x "$candidate/bin/java" ] || return 1
    [ -x "$candidate/bin/javac" ] || return 1
    "$candidate/bin/javac" -version 2>&1 | grep -Eq '^javac 21([.]|$)' || return 1
    "$candidate/bin/java" -version 2>&1 | grep -Eq 'version "21([.]|")'
}

is_jdk21_path() {
    command -v java >/dev/null 2>&1 \
        && command -v javac >/dev/null 2>&1 \
        && javac -version 2>&1 | grep -Eq '^javac 21([.]|$)' \
        && java -version 2>&1 | grep -Eq 'version "21([.]|")'
}

gradle_java_home=${EVIDRILO_JAVA_HOME:-${JAVA_HOME:-}}
gradle_available=0
if [ -n "$gradle_java_home" ]; then
    if is_jdk21_home "$gradle_java_home"; then
        gradle_available=1
    else
        printf '%s\n' 'Configured Java home is incompatible; JDK 21 with java and javac is required.' >&2
        gradle_java_home=
        unset JAVA_HOME
    fi
fi

if [ "$gradle_available" -eq 0 ] && [ -z "$gradle_java_home" ] && is_jdk21_path; then
    gradle_available=1
fi

if [ "$gradle_available" -eq 0 ] && [ -z "$gradle_java_home" ]; then
    for candidate in \
        "${HOME:-}/.local/share/evidrilo/jdk21" \
        /usr/lib/jvm/java-21-openjdk \
        /usr/lib/jvm/java-21-openjdk-amd64
    do
        if is_jdk21_home "$candidate"; then
            gradle_java_home=$candidate
            gradle_available=1
            break
        fi
    done
fi

if [ "$gradle_available" -eq 1 ] && [ -n "$gradle_java_home" ]; then
    export JAVA_HOME="$gradle_java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ "$gradle_available" -eq 1 ]; then
    printf '%s\n' '1/4 Kotlin module tests, cross-target compilation, and Android release bundle'
    ./gradlew --gradle-user-home "$gradle_user_home" \
        --no-configuration-cache \
        :modules:core:jvmTest \
        :modules:core:compileKotlinJvm \
        :modules:core:compileKotlinIosSimulatorArm64 \
        :modules:core:compileDebugKotlinAndroid \
        :modules:domain:jvmTest \
        :modules:domain:compileKotlinJvm \
        :modules:data:jvmTest \
        :modules:data:compileKotlinJvm \
        :modules:design-system:jvmTest \
        :modules:design-system:compileKotlinJvm \
        :modules:application:jvmTest \
        :modules:application:compileKotlinJvm \
        :modules:application:compileKotlinIosSimulatorArm64 \
        :modules:application:compileDebugKotlinAndroid \
        :modules:features:jvmTest \
        :modules:features:compileKotlinJvm \
        :modules:features:compileKotlinIosSimulatorArm64 \
        :modules:features:compileDebugKotlinAndroid \
        :composeApp:jvmTest \
        :composeApp:compileKotlinJvm \
        :composeApp:compileKotlinIosSimulatorArm64 \
        :composeApp:compileDebugKotlinAndroid \
        :androidApp:bundleRelease
    bash scripts/release/check-mobile-release.sh "$repo_root" --require-android-artifact
else
    printf '%s\n' '1/4 Kotlin module tests, cross-target compilation, and Android release bundle: UNAVAILABLE (JDK 21 with java and javac is not installed)' >&2
    bash scripts/release/check-mobile-release.sh "$repo_root"
fi

printf '%s\n' '2/4 Contracts, repository-boundary, and asset checks'
node --test \
    contracts/contracts.test.mjs \
    platform/database/migrations/migrations.test.mjs \
    scripts/verification/check-public-package.test.mjs \
    scripts/security/check-github-safety.test.mjs \
    scripts/verification/check-mobile-platform.test.mjs \
    scripts/verification/check-deployment.test.mjs \
    scripts/verification/check-architecture-boundaries.test.mjs \
    scripts/worktrees/check-worktree-scope.test.mjs \
    scripts/release/check-version-alignment.test.mjs \
    scripts/release/validate-submission-assets.test.mjs \
    scripts/verification/validate-audio-assets.test.mjs \
    scripts/github/export-public-package.test.mjs
node scripts/verification/validate-audio-assets.mjs "$repo_root" --allow-empty
bash scripts/verification/check-architecture-boundaries.sh "$repo_root"
bash -n scripts/verification/check-public-package.sh scripts/verification/check-deployment.sh scripts/release/validate-submission-assets.sh scripts/github/export-public-package.sh scripts/security/check-github-safety.sh platform/database/integration/run-local-postgres-backup-restore-smoke.sh
bash scripts/verification/check-deployment.sh "$repo_root"
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    docker compose -f infra/environments/local/docker-compose.yml config --quiet
else
    printf '%s\n' 'Docker Compose config check: UNAVAILABLE (Docker Compose is not installed)'
fi

dotnet_root=""
if dotnet_root=$(evidrilo_dotnet_root "$repo_root" 2>/dev/null); then
    export DOTNET_ROOT="$dotnet_root"
    export PATH="$DOTNET_ROOT:$PATH"
fi

dotnet_command=$(command -v dotnet || true)
if [ -n "$dotnet_command" ]; then
    printf '%s\n' '3/4 ASP.NET Core API tests'
    "$dotnet_command" test platform/api.Tests/Evidrilo.Api.Tests.csproj
    printf '%s\n' '4/4 Projection worker tests'
    "$dotnet_command" test platform/worker.Tests/Evidrilo.Worker.Tests.csproj --configuration Release
else
    printf '%s\n' '3/4 ASP.NET Core API tests: UNAVAILABLE (the .NET SDK is not installed)'
    printf '%s\n' '4/4 Projection worker tests: UNAVAILABLE (the .NET SDK is not installed)'
fi
