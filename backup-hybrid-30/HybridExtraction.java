package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class HybridExtraction {

    private static final ObjectMapper mapper = new ObjectMapper();

    // These fields need additional semantic or conflict checks
    // before automatic promotion.
    private static final Set<String> REVIEW_ONLY = Set.of(
            "patient_age_years",
            "event_terms",
            "event_outcome",
            "seriousness_criteria"
    );

    public static void main(String[] args) throws Exception {

        String documentId = args.length > 0 ? args[0] : "D02";
        boolean replay = args.length > 1
                && "--replay".equals(args[1]);

        if (!documentId.matches("D\\d{2}")) {
            throw new IllegalArgumentException("Invalid document ID");
        }

        Path sourcePath = Path.of("documents", documentId + ".txt");
        Path catalogPath = Path.of("catalog/field_catalog.json");
        Path baselinePath = Path.of("out", documentId + ".json");
        Path outputPath = Path.of("out-hybrid", documentId + ".json");
        Path recordingPath = Path.of(
                "fixtures-hybrid",
                documentId + ".recording.json"
        );

        String document = Files.readString(sourcePath);
        JsonNode catalog = mapper.readTree(Files.readString(catalogPath));

        ObjectNode baseline = (ObjectNode) mapper.readTree(
                Files.readString(baselinePath)
        );

        ArrayNode cases = (ArrayNode) baseline.path("cases");

        if (cases.size() != 1) {
            throw new IllegalStateException(
                    "Hybrid merging currently supports exactly one patient. " +
                    "Found " + cases.size()
            );
        }

        ObjectNode patient = (ObjectNode) cases.get(0);
        ObjectNode existingFields = (ObjectNode) patient.path("fields");

        ArrayNode missingFields = mapper.createArrayNode();

        for (JsonNode field : catalog.path("fields")) {

            String id = field.path("id").asText();

            if (REVIEW_ONLY.contains(id)) {
                continue;
            }

            String status = existingFields.path(id)
                    .path("status")
                    .asText("unknown");

            if ("unknown".equals(status)) {
                missingFields.add(field.deepCopy());
            }
        }

        System.out.println("Processing " + documentId);
        System.out.println("Missing eligible fields: " + missingFields.size());

        Files.createDirectories(outputPath.getParent());

        if (missingFields.isEmpty()) {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(outputPath.toFile(), baseline);

            System.out.println("No eligible fields to recover.");
            return;
        }

        ObjectNode reducedCatalog = mapper.createObjectNode();
        reducedCatalog.set("fields", missingFields);

        ArrayNode recordings = mapper.createArrayNode();
        ObjectNode generated;

        if (replay) {

            JsonNode saved = mapper.readTree(
                    Files.readString(recordingPath)
            );

            if (!documentId.equals(
                    saved.path("document_id").asText())) {
                throw new IllegalStateException(
                        "Recording document ID mismatch"
                );
            }

            generated = (ObjectNode) mapper.readTree(
                    saved.path("response").asText()
            );

            System.out.println("Using hybrid recording (OFFLINE)");

        } else {

            ModelClient model = new OllamaModelClient();

            generated = BatchExtraction.extract(
                    document,
                    reducedCatalog,
                    model,
                    recordings
            );

            Files.createDirectories(recordingPath.getParent());

            ObjectNode recording = mapper.createObjectNode();
            recording.put("document_id", documentId);
            recording.put("model", "ollama/qwen2.5:3b");
            recording.set("batches", recordings);
            recording.put("response", generated.toString());

            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(recordingPath.toFile(), recording);
        }

        JsonNode generatedCases = generated.path("cases");

        if (!generatedCases.isArray() || generatedCases.size() != 1) {
            throw new IllegalStateException(
                    "Batch extraction returned an unexpected case count. " +
                    "Original output has not been modified."
            );
        }

        String originalLabel = patient.path("label").asText();
        String generatedLabel = generatedCases.get(0)
                .path("label").asText();

        boolean labelsMatch =
                originalLabel.equalsIgnoreCase(generatedLabel);

        boolean genericPlaceholder =
                generatedLabel.equalsIgnoreCase("Patient identifier")
                || generatedLabel.equalsIgnoreCase("Patient label")
                || generatedLabel.equalsIgnoreCase("Patient");

        if (!labelsMatch && !genericPlaceholder) {
            throw new IllegalStateException(
                    "Patient labels differ; refusing automatic merge: " +
                    originalLabel + " vs " + generatedLabel
            );
        }

        if (genericPlaceholder && !labelsMatch) {
            System.out.println(
                    "Single-case placeholder label detected; " +
                    "preserving baseline patient label: " + originalLabel
            );
        }

        JsonNode candidates = generatedCases.get(0).path("fields");

        int recovered = 0;

        for (JsonNode catalogField : missingFields) {

            String id = catalogField.path("id").asText();

            JsonNode candidate = candidates.path(id);

            ObjectNode validated = ExtractionService.validateField(
                    candidate,
                    catalogField,
                    document
            );

            if (!"extracted".equals(
                    validated.path("status").asText())) {
                continue;
            }

            // Normalize concomitant medications before accepting.
            if ("concomitant_drugs".equals(id)) {

                JsonNode value = validated.path("value");

                if (!value.isArray()) {
                    continue;
                }

                String suspect = existingFields
                        .path("suspect_drug_name")
                        .path("value")
                        .asText("")
                        .trim();

                ArrayNode cleaned = mapper.createArrayNode();

                for (JsonNode item : value) {

                    if (!item.isTextual()) {
                        continue;
                    }

                    for (String part : item.asText().split(",")) {

                        String drug = part.trim();

                        if (drug.isEmpty()) {
                            continue;
                        }

                        if (!suspect.isEmpty()
                                && drug.equalsIgnoreCase(suspect)) {
                            continue;
                        }

                        boolean duplicate = false;

                        for (JsonNode existing : cleaned) {
                            if (existing.asText().equalsIgnoreCase(drug)) {
                                duplicate = true;
                                break;
                            }
                        }

                        if (!duplicate) {
                            cleaned.add(drug);
                        }
                    }
                }

                if (cleaned.isEmpty()) {
                    continue;
                }

                validated.set("value", cleaned);
            }

            // Recovered values remain low confidence until
            // additional semantic checks are implemented.
            validated.put("confidence", "low");

            existingFields.set(id, validated);
            recovered++;

            System.out.println("Recovered: " + id);
        }

        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(outputPath.toFile(), baseline);

        System.out.println("Hybrid extraction completed!");
        System.out.println("Recovered fields: " + recovered);
        System.out.println("Output: " + outputPath);
    }
}
