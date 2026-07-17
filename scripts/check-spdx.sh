#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# check-spdx.sh — CI gate: fail if any tracked source file is missing the SPDX
# license identifier in its first three lines. Companion to add-spdx.sh, which
# applies the header. Both scripts stay in-repo so external contributors can run
# them locally before opening a PR.
#
# Scope: tracked *.kt *.kts *.py *.proto *.ts, excluding generated / vendored
# trees (build output, ANTLR-generated parsers, protobuf *_pb2 stubs, node_modules,
# graphify-out) and golden expected-output fixtures.
#
# Why golden fixtures are excluded (added 2026-07-13 after they broke the build):
# a file under */test/golden/ is not source we license — it is the exact bytes an
# emitter is asserted to produce. The SV-P2·S1 sweep stamped the eleven Polars /
# pg_adbc / transfer goldens because they end in `.py`, which made every golden
# comparison fail on line 1 (expected the SPDX header, got `import polars as pl`)
# and left the `kotlin` CI job red. The rule of thumb: if a test compares a file
# BYTE-FOR-BYTE against program output, a licence header is a defect, not
# compliance. Keep this exclusion and add-spdx.sh's in sync.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

spdx='SPDX-License-Identifier: Apache-2.0'
offenders=()

while IFS= read -r f; do
  case "$f" in
    build/*|*/build/*)               continue ;;
    */generated/*)                   continue ;;
    */vendor/*)                      continue ;;  # third-party (grammars-v4 PostgreSQL base classes)
    docs/*/examples/*)               continue ;;  # vendored samples (byx=MIT/Microsoft, kyx=Alteryx)
    infra/backstage/*)               continue ;;  # third-party Backstage scaffold (Apache-2.0 upstream, "The Backstage Authors")
    */test/golden/*)                 continue ;;  # golden expected-output fixtures — DATA, not source (see note below)
    *_pb2.py|*_pb2_grpc.py|*_pb2.pyi) continue ;;
    node_modules/*|*/node_modules/*) continue ;;
    graphify-out/*|*/graphify-out/*) continue ;;
  esac
  header="$(head -n 3 "$f")"
  if ! grep -qF -- "$spdx" <<<"$header"; then
    offenders+=("$f")
  fi
done < <(git ls-files -- '*.kt' '*.kts' '*.py' '*.proto' '*.ts')

if [ ${#offenders[@]} -gt 0 ]; then
  printf 'Missing "%s" in the first 3 lines of %d file(s):\n' "$spdx" "${#offenders[@]}"
  printf '  %s\n' "${offenders[@]}"
  echo
  echo 'Fix: ./scripts/add-spdx.sh'
  exit 1
fi

echo "SPDX header check: OK (all tracked source files carry the Apache-2.0 identifier)."
