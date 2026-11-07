#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")"

echo "===================================="
echo " Case Intake - Offline Replay"
echo "===================================="

mvn -q -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile=target/classpath.txt

CP="target/classes:$(cat target/classpath.txt)"

for i in {1..8}; do

    DOC=$(printf "D%02d" "$i")
    RECORDING="fixtures/$DOC.recording.json"

    if [ ! -f "$RECORDING" ]; then
        echo "ERROR: Missing recording for $DOC"
        exit 1
    fi

    echo "Replaying $DOC..."

    java -cp "$CP" dev.caseintake.ExtractionService "$DOC" --replay

done

echo ""
echo "===================================="
echo " Offline replay completed"
echo "===================================="
