package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtractionQualityChecks {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Pattern AGE_PATTERN = Pattern.compile(
            "(?i)\\b(?:age\\s*:\\s*|aged\\s+|)(\\d{1,3})[- ]year[- ]old\\b"
    );

    private static final Pattern AGE_LABEL_PATTERN = Pattern.compile(
            "(?i)\\bage\\s*:\\s*(\\d{1,3})\\b"
    );

    public static void checkAgeConflict(
            ObjectNode fields,
            String document
    ) {

        JsonNode current = fields.path("patient_age_years");

        if (!"extracted".equals(current.path("status").asText())) {
            return;
        }

        Set<Integer> ages = new LinkedHashSet<>();

        Matcher labelled = AGE_LABEL_PATTERN.matcher(document);

        while (labelled.find()) {
            ages.add(Integer.parseInt(labelled.group(1)));
        }

        Matcher narrative = AGE_PATTERN.matcher(document);

        while (narrative.find()) {
            ages.add(Integer.parseInt(narrative.group(1)));
        }

        if (ages.size() > 1) {

            ObjectNode conflict = mapper.createObjectNode();
            conflict.put("status", "conflict");
            conflict.put(
                    "note",
                    "Conflicting patient ages reported: " + ages
            );

            fields.set("patient_age_years", conflict);

            System.out.println(
                    "Age conflict detected: " + ages
            );
        }
    }

    public static void checkEventTerms(ObjectNode fields) {

        JsonNode current = fields.path("event_terms");

        if (!"extracted".equals(current.path("status").asText())) {
            return;
        }

        JsonNode value = current.path("value");

        if (!value.isArray()) {
            return;
        }

        for (JsonNode item : value) {

            if (item.isTextual() && item.asText().contains(",")) {

                ObjectNode unknown = mapper.createObjectNode();
                unknown.put("status", "unknown");
                unknown.put(
                        "note",
                        "Combined event terms require separate verification"
                );

                fields.set("event_terms", unknown);

                System.out.println(
                        "Combined event terms flagged for review"
                );

                return;
            }
        }
    }
}
