#!/usr/bin/env bash
set -Eeuo pipefail

script_directory=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repository_root=$(CDPATH= cd -- "$script_directory/../.." && pwd)
source "$script_directory/agentctl-supabase-env.sh"

evidrilo_agentctl_require
project_ref=${EVIDRILO_SUPABASE_PROJECT_REF:-}
if [[ -z "$project_ref" ]]; then
  project_ref=$(evidrilo_agentctl_supabase_project_ref)
fi
supabase_url=$(evidrilo_supabase_url "$project_ref")
publishable_key=$(evidrilo_agentctl_supabase_publishable_key "$project_ref")
api_port=${EVIDRILO_API_PORT:-5080}
android_api_host=${EVIDRILO_ANDROID_API_HOST:-127.0.0.1}
api_url="http://${android_api_host}:${api_port}"

gradle_properties=(
  "-PsupabaseUrl=$supabase_url"
  "-PsupabasePublishableKey=$publishable_key"
  "-PevidriloApiBaseUrl=$api_url"
)
gradle_environment=()
if [[ -n ${EVIDRILO_GRADLE_USER_HOME:-} ]]; then
  gradle_environment+=("GRADLE_USER_HOME=$EVIDRILO_GRADLE_USER_HOME")
elif [[ -n ${GRADLE_USER_HOME:-} ]]; then
  gradle_environment+=("GRADLE_USER_HOME=$GRADLE_USER_HOME")
fi

(
  cd "$repository_root"
  env "${gradle_environment[@]}" ./gradlew --no-configuration-cache --console=plain \
    "${gradle_properties[@]}" :androidApp:assembleDebug
)

if [[ ${EVIDRILO_INSTALL_ANDROID:-0} == 1 ]]; then
  adb_path=${ANDROID_ADB_PATH:-adb}
  "$adb_path" reverse "tcp:${api_port}" "tcp:${api_port}" >/dev/null
  "$adb_path" install -r "$repository_root/apps/android/build/outputs/apk/debug/androidApp-debug.apk" >/dev/null
  printf '%s\n' 'ANDROID_DEBUG_APK_INSTALLED'
fi

printf '%s\n' "$repository_root/apps/android/build/outputs/apk/debug/androidApp-debug.apk"
