#!/usr/bin/env bash
# Compile the actual Gradle-free Hub, then inspect its bundled legal resources
# without opening the Hub or writing acceptance into the real user's home.
set -euo pipefail
ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_DIR="$(mktemp -d "${TMPDIR:-/tmp}/jvn-license-launch.XXXXXX")"
trap 'rm -rf "$TEST_DIR"' EXIT
export JVN_TEST_JAVA="$(command -v java)"
export JVN_TEST_LEGAL_DIR="$TEST_DIR/legal"
mkdir -p "$TEST_DIR/bin"
cat > "$TEST_DIR/bin/java" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
while (($#)); do
  if [[ "$1" == "-cp" ]]; then
    shift
    exec "$JVN_TEST_JAVA" "-Djvn.legalDir=$JVN_TEST_LEGAL_DIR" -cp "$1" com.jvn.licensing.LicenseCli status
  fi
  shift
done
exit 2
SHIM
chmod +x "$TEST_DIR/bin/java"
export PATH="$TEST_DIR/bin:$PATH"
export JVN_HUB_CACHE_DIR="$TEST_DIR/cache"
bash "$ROOT_DIR/scripts/launch-hub.sh" --direct > "$TEST_DIR/first.txt"
grep -q 'NOT_ACCEPTED' "$TEST_DIR/first.txt"
test ! -e "$JVN_TEST_LEGAL_DIR"
cmp "$ROOT_DIR/LICENSE.md" "$TEST_DIR/cache/classes/com/jvn/licensing/LICENSE.md"
cmp "$ROOT_DIR/TERMS.md" "$TEST_DIR/cache/classes/com/jvn/licensing/TERMS.md"
cp -p "$TEST_DIR/cache/compile.stamp" "$TEST_DIR/original.stamp"
bash "$ROOT_DIR/scripts/launch-hub.sh" --direct > "$TEST_DIR/second.txt"
test ! "$TEST_DIR/cache/compile.stamp" -nt "$TEST_DIR/original.stamp"
grep -q 'NOT_ACCEPTED' "$TEST_DIR/second.txt"
printf 'PASS: direct launcher compiles, includes legal resources, reuses its cache, and does not record consent.\n'
