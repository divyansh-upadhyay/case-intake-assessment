# Case Intake — LLM-assisted adverse-event report review

Java 21 / Spring Boot 3.3.13 backend and React 19 / TypeScript reviewer for extracting structured case fields from source reports. The extraction pipeline uses a catalog of 16 fields, local Ollama model responses, source-grounded provenance, and offline replay.

## Prerequisites

- Java 21 and Maven
- Node.js 20+ and npm
- Python 3.9+ for the supplied checker
- Optional for **live** extraction: Ollama running locally with `qwen2.5:3b` available

Run commands from the **repository root** unless stated otherwise.

## Start the application

Backend (terminal 1):

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
```

Frontend (terminal 2):

```bash
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite (normally `http://127.0.0.1:5173`; if occupied, Vite chooses another port). The backend defaults to port 8080. Ensure `frontend/.env.local` points to the correct backend URL for your setup.

The reviewer loads the document and catalog, displays extracted fields alongside source text, and supports reviewer corrections, verification and notes. Review changes are persisted in `reviewed-cases/`.

## API endpoints

- `GET /api/catalog` — field catalog
- `GET /api/documents` — available documents
- `GET /api/cases/{id}` — extracted case
- `GET /api/cases/{id}/review` — saved review
- `PUT /api/cases/{id}/review` — persist reviewer edits

## Extraction and replay

The live extraction path sends a catalog-driven prompt to Ollama through the Java model client, normalizes and validates returned fields, checks supporting evidence against the original source, and writes `out/D01.json` through `out/D08.json`. Model exchanges are recorded under `fixtures/DXX.recording.json`.

For **offline, deterministic replay**, run:

```bash
./replay.sh
```

Replay uses the recorded model responses and runs the Java extraction/validation path again; it does **not** require Ollama. The eight recorded documents D01–D08 have been replayed successfully. SHA-256 comparisons of output files across consecutive replay runs showed no differences.

**Important:** The current `replay.sh` is verified with the default catalog. Optional alternate-catalog support has not been verified.

## Verification

Backend tests:

```bash
mvn clean test
```

Frontend checks:

```bash
cd frontend
npm run build
npx tsc --noEmit
```

Check `frontend/package.json` before running `npm test`: the current test script may be a placeholder rather than a real automated frontend test suite.

Supplied assessment checker (from repository root, after replay):

```bash
python3 tools/check.py \
  --catalog catalog/field_catalog.json \
  --answers answers \
  --out out \
  --docs documents
```

## Latest verified results (7 November 2026)

The **targeted recovery** output (`out-targeted/`) scored **45/64 (70.3%)** across D01, D02, D03 and D05; **0 wrong values** at high/medium/low confidence, **43/43 grounded extracted fields**, **19 unknown answer-bearing fields**, and **0 case-count errors**. The previously verified Java suite had **16 passing tests**. These are local measurements supplied during development, not a claim that the newly added tests have been executed.

**Output distinction:** The API may still read `out/` rather than `out-targeted/`; check the controller implementation before presenting the improved score as the API's output. The offline replay baseline and targeted-recovery score are separate measurements.

## New automated checks

The added `ExtractionProvenanceTest` checks evidence matching, rejection of missing/unsupported evidence, unknown/conflict preservation, and Unicode code-point offsets.

```bash
mvn clean test
```

The added frontend API integration checks require a running backend and an API serving the updated D03 extraction (`country=GB`):

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
# In another terminal:
cd frontend
npm ci
npm run build
CASE_INTAKE_E2E=1 npm test
```

`npm test` alone skips the live API integration checks. The API checks do **not** automate clicking the browser UI; visually verify document selection, evidence highlighting, editing, verification, and review persistence before submission.

## Repository layout

- `src/main/java/dev/caseintake/` — Spring Boot APIs, model client, extraction and normalization
- `src/test/` — backend tests
- `frontend/` — React reviewer
- `catalog/field_catalog.json` — field definitions and constraints
- `documents/` — source reports
- `fixtures/DXX.recording.json` — recorded model responses for replay
- `out/` — generated extraction outputs
- `reviewed-cases/` — saved reviewer changes (created as needed)
- `tools/check.py` — supplied scoring tool
- `replay.sh` — Java offline replay entry point

## Known limitations

The model can omit fields, produce inaccurate evidence quotes, combine distinct events, and fail to identify contradictory values. Catalog-aware normalization and source-grounded evidence checks reduce but do not eliminate these issues. Full JSON Schema compliance, multiple/no-case scenarios, custom-catalog replay, and comprehensive frontend interaction tests require further verification. Review all extracted information before operational use.
