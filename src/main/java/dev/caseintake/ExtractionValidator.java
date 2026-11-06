package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExtractionValidator {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Set<String> VALID_STATUSES =
            Set.of("extracted", "unknown", "conflict");

    public static List<String> validate(
            Path documentPath,
            Path extractionPath,
            Path catalogPath
    ) throws Exception {

        List<String> errors = new ArrayList<>();

        String documentText = Files.readString(documentPath);

        JsonNode extraction = mapper.readTree(
                Files.readString(extractionPath)
        );

        JsonNode catalog = mapper.readTree(
                Files.readString(catalogPath)
        );

        JsonNode catalogFields = catalog.path("fields");

        if (!catalogFields.isArray()) {
            errors.add("Catalog fields must be an array");
            return errors;
        }

        JsonNode cases = extraction.path("cases");

        if (!cases.isArray()) {
            errors.add("Cases must be an array");
            return errors;
        }

        for (JsonNode caseNode : cases) {

            int caseIndex = caseNode.path("case_index").asInt(-1);

            JsonNode extractedFields = caseNode.path("fields");

            if (!extractedFields.isObject()) {
                errors.add(
                        "Case " + caseIndex + ": fields must be an object"
                );
                continue;
            }

            for (JsonNode catalogField : catalogFields) {

                String fieldId = catalogField.path("id").asText();

                JsonNode field = extractedFields.path(fieldId);

                if (field.isMissingNode()) {
                    errors.add(
                            "Case " + caseIndex
                                    + ": Missing field " + fieldId
                    );
                    continue;
                }

                String status = field.path("status").asText();

                if (!VALID_STATUSES.contains(status)) {
                    errors.add(
                            fieldId + ": Invalid status " + status
                    );
                    continue;
                }

                if ("extracted".equals(status)) {

                    if (!field.has("value") ||
                            field.get("value").isNull()) {

                        errors.add(
                                fieldId + ": Extracted value is missing"
                        );
                    }

                    validateProvenance(
                            documentText,
                            field,
                            fieldId,
                            errors
                    );
                }
            }
        }

        return errors;
    }

    private static void validateProvenance(
            String documentText,
            JsonNode field,
            String fieldId,
            List<String> errors
    ) {

        JsonNode provenance = field.path("provenance");

        if (!provenance.isArray() || provenance.isEmpty()) {
            errors.add(
                    fieldId + ": Missing provenance"
            );
            return;
        }

        for (JsonNode evidence : provenance) {

            String quote = evidence.path("quote").asText();

            int start = evidence.path("start").asInt(-1);
            int end = evidence.path("end").asInt(-1);

            if (start < 0 ||
                    end < start ||
                    end > documentText.length()) {

                errors.add(
                        fieldId + ": Invalid provenance offsets"
                );
                continue;
            }

            String actualQuote = documentText.substring(start, end);

            if (!actualQuote.equals(quote)) {

                errors.add(
                        fieldId
                                + ": Provenance mismatch. Expected: "
                                + quote
                                + ", Actual: "
                                + actualQuote
                );
            }
        }
    }
}