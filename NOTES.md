# Case Intake — Assessment Notes

## 1. Implementation overview

The project implements a Java 21 / Spring Boot 3.3.13 extraction backend and React 19 / TypeScript reviewer. A local Ollama model (`qwen2.5:3b`) returns candidate structured fields. The Java pipeline reads `catalog/field_catalog.json`, normalizes catalog types and codes, validates extracted values and evidence, and writes extraction JSON to `out/`. A separate review API stores human edits in `reviewed-cases/`.

The catalog currently defines 16 fields. The implementation uses a file-based workflow rather than a database.

## 2. Model use and reproducibility

Live extraction uses `OllamaModelClient` with the Ollama generate API. The pipeline stores model-response recordings as `fixtures/DXX.recording.json` for D01–D08.

`./replay.sh` invokes the Java extraction service with `--replay` for each recorded document. It reads the saved response and regenerates `out/DXX.json` without contacting Ollama. All eight documents replayed successfully. SHA-256 hashes of all eight output files matched across two consecutive replay runs.

**Recording limitation:** The current live path may save the base prompt rather than the exact expanded/retry prompt sent to the model. The response is recorded and usable for deterministic replay, but prompt audit fidelity should be improved before claiming exact request reproduction. Original recordings should not be rewritten without a genuine source record.

## 3. Validation and evidence

The Java extraction service normalizes common types (including ISO dates and slash-separated dates), checks field values against catalog constraints, and requires source evidence for extracted values. For accepted evidence, it stores the exact matched source substring and Unicode code-point start/end offsets. Fields that fail validation are marked `unknown` with a reason; the pipeline also supports `conflict` status from the model.

The implementation has not been independently verified against the complete supplied JSON Schema or all multiple-case/zero-case scenarios. No claim of full schema conformance is made.

## 4. Latest checker results — 7 November 2026

The targeted extraction output (`out-targeted/`) was reported at **45/64 field points (70.3%)** on four scored documents, with **0 incorrect values**, **19 unknown answer-bearing fields**, **0 case-count errors**, and **43/43 extracted fields grounded in source text (100%)**. The existing Java suite passed **16/16 tests** before the new regression tests were added. The original `out/` pipeline and offline replay may yield different scores; do not conflate them.

Command used for the targeted score:

```bash
python3 tools/check.py --catalog catalog/field_catalog.json --answers answers --out out-targeted --docs documents
```

## 5. Testing and reviewer workflow

- `mvn clean test`: **16 tests passed, zero failures** (7 November 2026).
- `./replay.sh`: **8/8 documents completed**.
- React/Vite frontend has started successfully; production build has been reported successful in development.
- Reviewer API endpoints include catalog, document listing, extracted cases, read review, and save review.
- Reviewer edits, verification and notes are stored separately from model-generated extraction results.

Automated frontend tests are not yet demonstrated; a placeholder `npm test` script is not evidence of coverage. Full end-to-end browser verification remains recommended.

## 6. Architecture and tradeoffs

- **Local Ollama:** avoids a paid API dependency but the small model can omit fields or mishandle ambiguity.
- **Catalog-driven extraction:** field definitions can evolve without hardcoding all field names; end-to-end alternate-catalog behavior needs further testing.
- **Strict provenance:** unmatched model quotes are rejected rather than silently treated as valid evidence.
- **File-based outputs:** easy to inspect, replay and review for the exercise; lacks database features for concurrent production workflows.
- **Offline replay:** enables deterministic review without network or model availability, but depends on retaining genuine recordings and compatible Java validation code.
- **Human review:** corrections and verification are explicitly separate from raw model output.

## 7. AI assistance and UI design prompt

AI assistance was used for architecture planning, code drafting and debugging, test design, UI layout, and documentation. Generated suggestions were reviewed through compilation, tests, replay and checker output; those checks do not establish comprehensive correctness.

The UI design prompt recorded during development was:

> Design a restrained clinical case-review workspace with a dark navy document sidebar, an original plain-text report panel, and a catalog-driven extraction panel. Prioritize readable typography, clear high/medium/low confidence badges, and clickable evidence that highlights the corresponding source quote. Use React 19 and TypeScript.

## 8. Known limitations and next work

1. Targeted extraction quality is **70.3%** on the supplied scored set, with no reported incorrect values; results still require human review.
2. The model omits some catalog fields, including fields present in source documents.
3. Some model evidence quotes are not exact source substrings, causing valid-looking values to be rejected; evidence matching must remain grounded.
4. Event lists and contradictory patient values need better handling.
5. Full JSON Schema validation, multiple/no-case behavior, and custom-catalog replay need explicit tests.
6. The recorded `prompt` may differ from the actual expanded live prompt; correct this for future recordings.
7. Automated frontend API integration checks are provided but must be executed against a running backend; browser interaction verification remains incomplete.
8. The repository contains local backup/experimental directories that should be excluded from a clean submission package, without deleting the original evidence or recordings.

## 9. Submission status

Core backend, reviewer workflow and offline replay are implemented and usable for a technical walkthrough. Before final submission, verify frontend startup, review persistence, packaging, and the precise assessment requirements. This project is a functional prototype with explicitly documented extraction-quality limitations, not a production-ready clinical system.
