package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TargetedExtraction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern AGE = Pattern.compile(
            "(?im)^\\s*Age:\\s*(\\d{1,3})\\s*$"
    );

    private static final Pattern SEX = Pattern.compile(
            "(?im)^\\s*Sex:\\s*(Male|Female)\\s*$"
    );

    private static final Pattern START_DATE = Pattern.compile(
            "(?im)^\\s*Treatment started:\\s*(\\d{2}/\\d{2}/\\d{4})\\s*$"
    );

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu")
                    .withResolverStyle(ResolverStyle.STRICT);

    public static void main(String[] args) throws Exception {

        if (args.length != 1 || !args[0].matches("D\\d{2}")) {
            throw new IllegalArgumentException(
                    "Usage: TargetedExtraction D03"
            );
        }

        String documentId = args[0];

        Path documentPath = Path.of(
                "documents", documentId + ".txt"
        );

        Path inputPath = Path.of(
                "out-quality", documentId + ".json"
        );

        String document = Files.readString(documentPath);

        ObjectNode output = (ObjectNode) MAPPER.readTree(
                Files.readString(inputPath)
        );

        JsonNode catalog = MAPPER.readTree(
                Path.of("catalog/field_catalog.json").toFile()
        );

        Set<String> allowedFields = new HashSet<>();

        for (JsonNode field : catalog.path("fields")) {
            allowedFields.add(field.path("id").asText());
        }

        JsonNode cases = output.path("cases");

        if (!cases.isArray() || cases.size() != 1) {
            throw new IllegalStateException(
                    "Targeted extraction currently supports exactly one case"
            );
        }

        ObjectNode fields = (ObjectNode) cases.get(0).path("fields");

        int recovered = 0;

        recovered += recover(
                fields,
                allowedFields,
                "patient_age_years",
                AGE,
                document,
                "integer"
        );

        recovered += recover(
                fields,
                allowedFields,
                "patient_sex",
                SEX,
                document,
                "sex"
        );

        recovered += recover(
                fields,
                allowedFields,
                "suspect_drug_start_date",
                START_DATE,
                document,
                "date"
        );

        Path outputDir = Path.of("out-targeted");
        Files.createDirectories(outputDir);

        Path resultPath = outputDir.resolve(
                documentId + ".json"
        );

        MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(resultPath.toFile(), output);

        System.out.println(
                "Targeted extraction completed: " + documentId
        );

        System.out.println("Recovered fields: " + recovered);
        System.out.println("Output: " + resultPath);
    }

    private static int recover(
            ObjectNode fields,
            Set<String> allowedFields,
            String fieldId,
            Pattern pattern,
            String document,
            String type
    ) {

        if (!allowedFields.contains(fieldId)) {
            return 0;
        }

        JsonNode existing = fields.path(fieldId);

        if (!"unknown".equals(existing.path("status").asText())) {
            return 0;
        }

        Matcher matcher = pattern.matcher(document);

        if (!matcher.find()) {
            return 0;
        }

        String raw = matcher.group(1);

        // Do not silently choose between conflicting labelled values.
        if (matcher.find()) {
            return 0;
        }

        ObjectNode candidate = MAPPER.createObjectNode();
        candidate.put("status", "extracted");

        try {
            switch (type) {
                case "integer" -> {
                    int age = Integer.parseInt(raw);

                    if (age < 0 || age > 120) {
                        return 0;
                    }

                    candidate.put("value", age);
                }

                case "sex" ->
                        candidate.put(
                                "value",
                                raw.toLowerCase()
                        );

                case "date" -> {
                    LocalDate date = LocalDate.parse(
                            raw,
                            DATE_FORMAT
                    );

                    candidate.put(
                            "value",
                            date.toString()
                    );
                }

                default -> {
                    return 0;
                }
            }
        } catch (Exception e) {
            return 0;
        }

        // Match the exact supporting text and offsets.
        Matcher evidenceMatcher = pattern.matcher(document);

        if (!evidenceMatcher.find()) {
            return 0;
        }

        // Capture the full labelled evidence, not only the extracted value.
        int start = evidenceMatcher.start();
        int end = evidenceMatcher.end();

        ObjectNode evidence = MAPPER.createObjectNode();
        evidence.put("quote", document.substring(start, end));
        evidence.put("start", start);
        evidence.put("end", end);

        ArrayNode provenance = MAPPER.createArrayNode();
        provenance.add(evidence);

        candidate.put("confidence", "high");
        candidate.set("provenance", provenance);

        fields.set(fieldId, candidate);

        System.out.println(
                "Recovered: " + fieldId
                + " = " + candidate.path("value")
        );

        return 1;
    }
}
