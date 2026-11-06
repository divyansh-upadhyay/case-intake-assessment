# Case Intake — assessment starter (INCOMPLETE)

Java 21 / Spring Boot 3 backend and React 19 / TypeScript reviewer. The original exercise documents, schemas, catalog and checker are included. **This is a starter, not a finished extraction submission.** No LLM extraction has been performed and no genuine model fixtures are supplied.

## Prerequisites (Ubuntu)

Java 21, Maven 3.9+, Node.js 20+ and npm, Python 3.9+.

## Run in IntelliJ

Open the repository root as a Maven project. Set Project SDK to Java 21. Run `dev.caseintake.Application` with the **working directory set to the repository root**. Or run `mvn spring-boot:run` from the repository root. The backend serves http://localhost:8080/api/documents.

In a separate terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Until genuine extraction output exists, documents show **Awaiting extraction**. D01 contains only a clearly labeled sample from the supplied example for UI development.

## Tests

```bash
mvn test
cd frontend && npm test && npx tsc --noEmit
```

## Remaining assessment work (mandatory)

1. Implement a live `ModelClient` adapter using a provider and an environment variable for the API key; never commit secrets.
2. Build a Java 21 command-line extraction pipeline that reads the catalog and all eight documents, handles model rate limits/retries, generates complete schema-valid results, validates values and exact Unicode provenance, and supports multi-patient/no-case reports.
3. Save genuine model requests/responses to `fixtures/` and implement deterministic Java replay from them. Add root `replay.sh` supporting an optional catalog path and exact reproduction of `out/` without a network.
4. Produce real `out/D01.json` through `out/D08.json` and run the supplied checker; paste actual checker output into `NOTES.md`.
5. Add meaningful backend/frontend tests, dynamic catalog editing controls, and error handling.
6. Add the required complete `NOTES.md` with observations, tradeoffs and the actual AI UI design prompt.
7. Create a Maven wrapper (`mvn -N wrapper:wrapper`) and verify `./mvnw test` works. Run `npm test` and `npx tsc --noEmit`.

Do not submit this starter unchanged. The brief says review stops when replay cannot reproduce the outputs or UI cannot start.
