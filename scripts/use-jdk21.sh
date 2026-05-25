#!/usr/bin/env bash
set -euo pipefail

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  echo "Source this script to update the current shell: source ./scripts/use-jdk21.sh" >&2
fi

resolve_candidate() {
  local candidate="$1"
  [[ -n "$candidate" && -x "$candidate/bin/java" ]] && printf '%s\n' "$candidate"
}

if [[ $# -gt 0 ]]; then
  resolved="$(resolve_candidate "$1" || true)"
else
  resolved=""
fi

if [[ -z "${resolved:-}" && -n "${JAVA_HOME_21_X64:-}" ]]; then
  resolved="$(resolve_candidate "$JAVA_HOME_21_X64" || true)"
fi

if [[ -z "${resolved:-}" ]]; then
  for root in /usr/lib/jvm /Library/Java/JavaVirtualMachines "${HOME}/.jdks"; do
    [[ -d "$root" ]] || continue
    while IFS= read -r candidate; do
      resolved="$(resolve_candidate "$candidate" || true)"
      [[ -n "$resolved" ]] && break 2
    done < <(find "$root" -maxdepth 1 -type d \( -name '*21*' -o -name 'jdk-21*' \) 2>/dev/null | sort -r)
  done
fi

if [[ -z "${resolved:-}" ]]; then
  echo "Unable to locate a JDK 21 installation. Pass the path as the first argument or set JAVA_HOME_21_X64." >&2
  return 1 2>/dev/null || exit 1
fi

export JAVA_HOME="$resolved"
export PATH="$JAVA_HOME/bin:$PATH"

echo "JAVA_HOME set to $JAVA_HOME"
java -version
