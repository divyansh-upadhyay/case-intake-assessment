#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

DOC_ID="${1:-D02}"

echo "Replaying ${DOC_ID} using recorded model response..."

mvn -q -DskipTests compile

mvn -q -DskipTests exec:java \
  -Dexec.mainClass=dev.caseintake.ExtractionService \
  -Dexec.args="${DOC_ID} --replay"

echo "Replay completed: ${DOC_ID}"
