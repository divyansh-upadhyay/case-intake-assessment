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
import java.util.*;
import java.util.regex.*;

public class TargetedFieldRecovery {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DateTimeFormatter DMY =
            DateTimeFormatter.ofPattern("dd/MM/uuuu")
                    .withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH)
                    .withResolverStyle(ResolverStyle.STRICT);

    public static void main(String[] args) throws Exception {

        if (args.length != 1 || !args[0].matches("D\\d{2}")) {
            throw new IllegalArgumentException(
                    "Usage: TargetedFieldRecovery D03"
            );
        }

        String id = args[0];

        String document = Files.readString(
                Path.of("documents", id + ".txt")
        );

        ObjectNode output = (ObjectNode) MAPPER.readTree(
                Path.of("out-targeted", id + ".json").toFile()
        );

        JsonNode catalog = MAPPER.readTree(
                Path.of("catalog/field_catalog.json").toFile()
        );

        Map<String, JsonNode> catalogFields = new HashMap<>();

        for (JsonNode field : catalog.path("fields")) {
            catalogFields.put(field.path("id").asText(), field);
        }

        JsonNode cases = output.path("cases");

        if (!cases.isArray() || cases.size() != 1) {
            throw new IllegalStateException(
                    "Exactly one case required"
            );
        }

        ObjectNode fields = (ObjectNode) cases.get(0).path("fields");

        int recovered = 0;

        recovered += recoverDate(
                fields, catalogFields, document,
                "report_date",
                "(?im)^\\s*Report completed:\\s*(\\d{2}/\\d{2}/\\d{4})",
                DMY
        );

        recovered += recoverDate(
                fields, catalogFields, document,
                "report_date",
                "(?im)^\\s*Report date:\\s*(\\d{1,2} [A-Za-z]+ \\d{4})",
                LONG_DATE
        );

        recovered += recoverDate(
                fields, catalogFields, document,
                "event_onset_date",
                "(?im)^\\s*On\\s+(\\d{2}/\\d{2}/\\d{4})\\s+the patient",
                DMY
        );

        recovered += recoverCountry(
                fields, catalogFields, document
        );

        recovered += recoverReporter(
                fields, catalogFields, document
        );

        Path outputDir = Path.of("out-recovered");
        Files.createDirectories(outputDir);

        Path result = outputDir.resolve(id + ".json");

        MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(result.toFile(), output);

        System.out.println("Recovered fields: " + recovered);
        System.out.println("Output: " + result);
    }

    private static int recoverDate(
            ObjectNode fields,
            Map<String, JsonNode> catalog,
            String document,
            String fieldId,
            String regex,
            DateTimeFormatter formatter
    ) {

        Matcher matcher = Pattern.compile(regex).matcher(document);

        if (!matcher.find()) {
            return 0;
        }

        String raw = matcher.group(1);
        String evidence = matcher.group();

        if (matcher.find()) {
            return 0;
        }

        try {
            String value = LocalDate.parse(raw, formatter).toString();

            return accept(
                    fields, catalog, document,
                    fieldId, value, evidence
            );

        } catch (Exception e) {
            return 0;
        }
    }

    private static int recoverCountry(
            ObjectNode fields,
            Map<String, JsonNode> catalog,
            String document
    ) {

        Map<String, String> countries = Map.of(
                "United Kingdom", "GB",
                "Australia", "AU"
        );

        Set<String> detected = new HashSet<>();
        String evidence = null;

        for (Map.Entry<String, String> entry : countries.entrySet()) {

            Pattern pattern = Pattern.compile(
                    "(?i)\\b" + Pattern.quote(entry.getKey()) + "\\b"
            );

            Matcher matcher = pattern.matcher(document);

            if (matcher.find()) {
                detected.add(entry.getValue());
                evidence = matcher.group();
            }
        }

        if (detected.size() != 1) {
            return 0;
        }

        return accept(
                fields, catalog, document,
                "country",
                detected.iterator().next(),
                evidence
        );
    }

    private static int recoverReporter(
            ObjectNode fields,
            Map<String, JsonNode> catalog,
            String document
    ) {

        Pattern nurse = Pattern.compile(
                "(?i)\\b(?:nurse practitioner|nurse prescriber)\\b"
        );

        Pattern physician = Pattern.compile(
                "(?i)\\b(?:consultant psychiatrist|reporting clinician:\\s*Dr\\b)"
        );

        Matcher nurseMatch = nurse.matcher(document);
        Matcher physicianMatch = physician.matcher(document);

        boolean isNurse = nurseMatch.find();
        boolean isPhysician = physicianMatch.find();

        if (isNurse == isPhysician) {
            return 0;
        }

        return accept(
                fields, catalog, document,
                "reporter_type",
                isNurse ? "nurse" : "physician",
                isNurse ? nurseMatch.group() : physicianMatch.group()
        );
    }

    private static int accept(
            ObjectNode fields,
            Map<String, JsonNode> catalog,
            String document,
            String fieldId,
            String value,
            String evidence
    ) {

        if (!catalog.containsKey(fieldId)) {
            return 0;
        }

        JsonNode existing = fields.path(fieldId);

        if (!"unknown".equals(existing.path("status").asText())) {
            return 0;
        }

        ObjectNode candidate = MAPPER.createObjectNode();

        candidate.put("status", "extracted");
        candidate.put("value", value);
        candidate.put("confidence", "high");

        ObjectNode source = MAPPER.createObjectNode();
        source.put("quote", evidence);

        ArrayNode provenance = MAPPER.createArrayNode();
        provenance.add(source);

        candidate.set("provenance", provenance);

        // Reuse the existing catalog and provenance validator.
        ObjectNode validated = ExtractionService.validateField(
                candidate,
                catalog.get(fieldId),
                document
        );

        if (!"extracted".equals(
                validated.path("status").asText()
        )) {
            System.out.println(
                    "Rejected: " + fieldId + " - "
                    + validated.path("note").asText()
            );
            return 0;
        }

        fields.set(fieldId, validated);

        System.out.println(
                "Recovered: " + fieldId
                + " = " + validated.path("value")
        );

        return 1;
    }
}
