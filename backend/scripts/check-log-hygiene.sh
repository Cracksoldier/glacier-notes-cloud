#!/usr/bin/env bash
# Enforces the "never log note content, checklist text, filenames, passwords, or tokens" rule
# (docs/ARCHITECTURE.md). This is a coarse allowlist-style heuristic, not sound taint analysis: it
# flags any LOG.<method>(...) call whose full argument list (which may span several lines) contains
# an identifier that plausibly carries user content or a secret. It cannot detect indirection
# (e.g. logging a local variable that was assigned from note content several lines earlier), and it
# may need new patterns as the logging surface grows. Treat a match as a signal to review, and widen
# this list deliberately rather than suppressing it.
set -euo pipefail

cd "$(dirname "$0")/../.."

SUSPICIOUS_PATTERN='\.content\(\)|\.title\(\)|\.text\(\)|\.password\b|\.token\b|password_hash|\.fileName\(\)|\.originalFileName\(\)|checklistText|noteContent'

files=$(grep -rl --include='*.java' -E 'Logger\.getLogger\(|= *Logger\.' backend/src/main/java || true)

if [ -z "$files" ]; then
  echo "check-log-hygiene: no logging call sites found under backend/src/main/java"
  exit 0
fi

violations=0
for file in $files; do
  while IFS= read -r -d '' block; do
    if grep -qEi "$SUSPICIOUS_PATTERN" <<<"$block"; then
      echo "check-log-hygiene: possible prohibited-field logging in $file:"
      echo "$block"
      echo "---"
      violations=$((violations + 1))
    fi
  done < <(grep -Pzo '(?s)\bLOG\.\w+\(.*?\);' "$file")
done

if [ "$violations" -gt 0 ]; then
  echo "check-log-hygiene: found $violations suspicious log call(s). See docs/SECURITY.md for the log-hygiene rule."
  exit 1
fi

echo "check-log-hygiene: no prohibited-field log calls detected across $(echo "$files" | wc -l) file(s)."
