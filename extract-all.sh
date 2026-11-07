#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "=================================="
echo "CASE INTAKE - BATCH EXTRACTION"
echo "=================================="

mvn -q -DskipTests compile

for i in {1..8}; do
    printf -v DOC_ID "D%02d" "$i"

    echo ""
    echo "Processing ${DOC_ID}..."

    mvn -q -DskipTests exec:java \
      -Dexec.mainClass=dev.caseintake.ExtractionService \
      -Dexec.args="${DOC_ID}"

    echo "Completed ${DOC_ID}"
done

echo ""
echo "All documents processed!"
