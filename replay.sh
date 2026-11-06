#!/usr/bin/env bash

set -euo pipefail

CATALOG="${1:-catalog/field_catalog.json}"

mvn -q -DskipTests compile

mvn -q -DskipTests exec:java \
  -Dexec.mainClass=dev.caseintake.ReplayRunner \
  -Dexec.args="$CATALOG"
