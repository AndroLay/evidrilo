#!/usr/bin/env bash
set -Eeuo pipefail

# Resolve the already-authorized Supabase connection without persisting or
# printing credential values. Callers should capture the key in a variable and
# pass it directly to the short-lived local process that needs it.

evidrilo_agentctl_require() {
  if ! command -v agentctl >/dev/null 2>&1; then
    printf '%s\n' 'agentctl is required for the local Supabase bootstrap.' >&2
    return 1
  fi
  if ! command -v node >/dev/null 2>&1; then
    printf '%s\n' 'node is required to parse agentctl metadata.' >&2
    return 1
  fi
  if ! command -v curl >/dev/null 2>&1; then
    printf '%s\n' 'curl is required to resolve the Supabase client key.' >&2
    return 1
  fi
  if ! command -v secret-tool >/dev/null 2>&1; then
    printf '%s\n' 'secret-tool is required to read the existing agentctl credential.' >&2
    return 1
  fi
}

evidrilo_agentctl_supabase_project_ref() {
  local project_name=${EVIDRILO_SUPABASE_PROJECT_NAME:-Evidrilo}
  local project_list
  if ! project_list=$(agentctl supabase project-list 2>/dev/null); then
    printf '%s\n' 'Unable to read the Supabase project list through agentctl.' >&2
    return 1
  fi

  printf '%s' "$project_list" | EVIDRILO_SUPABASE_PROJECT_NAME="$project_name" node -e '
let input = "";
process.stdin.on("data", chunk => { input += chunk; });
process.stdin.on("end", () => {
  try {
    const projects = JSON.parse(input);
    const wanted = (process.env.EVIDRILO_SUPABASE_PROJECT_NAME || "Evidrilo").trim().toLowerCase();
    const matches = Array.isArray(projects)
      ? projects.filter(project => String(project.name || "").trim().toLowerCase() === wanted)
      : [];
    if (matches.length !== 1 || !/^[A-Za-z0-9_-]+$/.test(String(matches[0].ref || ""))) {
      process.exitCode = 1;
      return;
    }
    process.stdout.write(String(matches[0].ref));
  } catch {
    process.exitCode = 1;
  }
});
'
}

evidrilo_agentctl_supabase_publishable_key() {
  local project_ref=$1
  local api_keys
  if [[ ! "$project_ref" =~ ^[A-Za-z0-9_-]+$ ]]; then
    printf '%s\n' 'The Supabase project reference is invalid.' >&2
    return 1
  fi
  if api_keys=$(agentctl api request supabase GET "/v1/projects/${project_ref}/api-keys" --project "$project_ref" 2>/dev/null); then
    if printf '%s' "$api_keys" | node -e '
let input = "";
process.stdin.on("data", chunk => { input += chunk; });
process.stdin.on("end", () => {
  try {
    const entries = JSON.parse(input);
    const usable = Array.isArray(entries)
      ? entries.filter(entry => (entry.name === "publishable" || entry.name === "anon") && typeof entry.api_key === "string" && entry.api_key.length >= 12 && entry.api_key !== "[redacted]")
      : [];
    const selected = usable.find(entry => entry.name === "publishable") || usable[0];
    if (!selected) {
      process.exitCode = 1;
      return;
    }
    process.stdout.write(selected.api_key);
  } catch {
    process.exitCode = 1;
  }
});
'; then
      return 0
    fi
  fi

  # agentctl intentionally redacts fields named api_key in human-facing JSON.
  # The Supabase anon/publishable key is safe for a mobile binary, but it still
  # must never be written to the repository or printed. Reuse the same
  # agentctl-managed Secret Service credential for one in-memory Management API
  # request and emit only the selected client key to the caller's command
  # substitution.
  local management_token response
  if ! management_token=$(secret-tool lookup service agentctl provider supabase profile default 2>/dev/null) || [[ -z "$management_token" ]]; then
    printf '%s\n' 'The agentctl Supabase credential is unavailable in the host keyring.' >&2
    return 1
  fi
  if ! response=$(curl --silent --show-error --fail --max-time 20 \
    -H "Authorization: Bearer ${management_token}" \
    "https://api.supabase.com/v1/projects/${project_ref}/api-keys" 2>/dev/null); then
    unset management_token
    printf '%s\n' 'Unable to read Supabase client-key metadata from the Management API.' >&2
    return 1
  fi
  unset management_token

  printf '%s' "$response" | node -e '
let input = "";
process.stdin.on("data", chunk => { input += chunk; });
process.stdin.on("end", () => {
  try {
    const entries = JSON.parse(input);
    const usable = Array.isArray(entries)
      ? entries.filter(entry => (entry.name === "publishable" || entry.name === "anon") && typeof entry.api_key === "string" && entry.api_key.length >= 12 && entry.api_key !== "[redacted]")
      : [];
    const selected = usable.find(entry => entry.name === "publishable") || usable.find(entry => entry.name === "anon");
    if (!selected) {
      process.exitCode = 1;
      return;
    }
    process.stdout.write(selected.api_key);
  } catch {
    process.exitCode = 1;
  }
});
'
}

evidrilo_supabase_url() {
  local project_ref=$1
  if [[ ! "$project_ref" =~ ^[A-Za-z0-9_-]+$ ]]; then
    printf '%s\n' 'The Supabase project reference is invalid.' >&2
    return 1
  fi
  printf 'https://%s.supabase.co' "$project_ref"
}
