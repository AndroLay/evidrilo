# Shared external toolchain and cache path resolution for local shell runners.
#
# This file intentionally defines functions only. It must be safe to source
# from a caller that already has its own shell options and cleanup traps.

evidrilo_external_cache_root() {
    if [[ -n "${EVIDRILO_CACHE_ROOT:-}" ]]; then
        printf '%s\n' "$EVIDRILO_CACHE_ROOT"
    elif [[ -n "${XDG_CACHE_HOME:-}" ]]; then
        printf '%s/evidrilo\n' "${XDG_CACHE_HOME%/}"
    else
        printf '%s/.cache/evidrilo\n' "${HOME:-/tmp}"
    fi
}

evidrilo_gradle_user_home() {
    if [[ -n "${EVIDRILO_GRADLE_USER_HOME:-}" ]]; then
        printf '%s\n' "$EVIDRILO_GRADLE_USER_HOME"
    elif [[ -n "${GRADLE_USER_HOME:-}" ]]; then
        printf '%s\n' "$GRADLE_USER_HOME"
    else
        printf '%s/gradle\n' "$(evidrilo_external_cache_root)"
    fi
}

evidrilo_nuget_packages() {
    if [[ -n "${EVIDRILO_NUGET_PACKAGES:-}" ]]; then
        printf '%s\n' "$EVIDRILO_NUGET_PACKAGES"
    elif [[ -n "${NUGET_PACKAGES:-}" ]]; then
        printf '%s\n' "$NUGET_PACKAGES"
    else
        printf '%s/nuget\n' "$(evidrilo_external_cache_root)"
    fi
}

evidrilo_dotnet_root() {
    local repo_root=${1:?repository root is required}
    local candidate dotnet_bin resolved_bin

    for candidate in "${EVIDRILO_DOTNET_ROOT:-}" "${DOTNET_ROOT:-}"; do
        if [[ -n "$candidate" && -x "$candidate/dotnet" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    if dotnet_bin=$(command -v dotnet 2>/dev/null); then
        resolved_bin=$(readlink -f "$dotnet_bin" 2>/dev/null || printf '%s' "$dotnet_bin")
        printf '%s\n' "$(dirname "$resolved_bin")"
        return 0
    fi

    for candidate in \
        "${HOME:-}/.dotnet" \
        "${HOME:-}/.local/share/evidrilo/dotnet" \
        "/usr/share/dotnet"
    do
        if [[ -x "$candidate/dotnet" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    if [[ "${EVIDRILO_ALLOW_REPOSITORY_TOOLCHAINS:-0}" == '1' ]]; then
        for candidate in "$repo_root/.dotnet-local" "$repo_root/.local/dotnet"; do
            if [[ -x "$candidate/dotnet" ]]; then
                printf '%s\n' "$candidate"
                return 0
            fi
        done
    fi

    return 1
}
