package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FieldNormalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JsonNode normalize(JsonNode value, JsonNode field) {

        if (value == null || value.isNull() || value.isMissingNode()) {
            return value;
        }

        String type = field.path("type").asText();

        if (type.equals("integer") && value.isTextual()) {
            String text = value.asText().trim();

            if (text.matches("\\d+")) {
                try {
                    return MAPPER.getNodeFactory()
                            .numberNode(Integer.parseInt(text));
                } catch (NumberFormatException ignored) {
                    return value;
                }
            }
        }

        if (type.equals("number") && value.isTextual()) {
    String text = value.asText().trim();

    Matcher matcher = Pattern.compile(
        "^([0-9]+(?:\\.[0-9]+)?)(?=\\s|$)"
    ).matcher(text);

    if (matcher.find()) {
        try {
            return MAPPER.getNodeFactory().numberNode(
                Double.parseDouble(matcher.group(1))
            );
        } catch (NumberFormatException ignored) {
            return value;
        }
    }
}

        if (type.equals("date") && value.isTextual()) {

            String text = value.asText().trim();

            List<DateTimeFormatter> formats = List.of(
                    DateTimeFormatter.ISO_LOCAL_DATE,
                    DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
            );

            for (DateTimeFormatter formatter : formats) {
                try {
                    LocalDate date = LocalDate.parse(text, formatter);
                    return TextNode.valueOf(date.toString());
                } catch (DateTimeParseException ignored) {
                    // Try the next supported format.
                }
            }
        }

        if (type.equals("coded") && value.isTextual()) {
            String normalized = normalizeCode(value.asText(), field);

            if (normalized != null) {
                return TextNode.valueOf(normalized);
            }
        }

        if (type.equals("coded_multi")) {

            ArrayNode result = MAPPER.createArrayNode();

            if (value.isTextual()) {
                String code = normalizeCode(value.asText(), field);

                if (code != null) {
                    result.add(code);
                    return result;
                }
            }

            if (value.isArray()) {
                for (JsonNode item : value) {
                    if (!item.isTextual()) return value;

                    String code = normalizeCode(item.asText(), field);

                    if (code == null) return value;

                    result.add(code);
                }

                return result;
            }
        }

        if (type.equals("string_list") && value.isTextual()) {
            ArrayNode result = MAPPER.createArrayNode();

            // Do not split commas automatically:
            // commas can be part of a single medical event.
            result.add(value.asText().trim());

            return result;
        }

        if (type.equals("string") && value.isTextual()) {

            String text = value.asText().trim();

            if (field.path("id").asText().equals("patient_initials")) {
                text = text.replace(".", "").replace(" ", "");
            }

            return TextNode.valueOf(text);
        }

        return value;
    }

    private static String normalizeCode(String text, JsonNode field) {

        String candidate = canonical(text);

        for (JsonNode option : field.path("options")) {

            String code = option.path("code").asText();
            String label = option.path("label").asText();

            if (candidate.equals(canonical(code))
                    || candidate.equals(canonical(label))) {
                return code;
            }
        }

        return null;
    }

    private static String canonical(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_-]+", "");
    }
private static String normalizeDate(String input) {

    if (input == null || input.isBlank()) {
        return input;
    }

    String value = input.trim();

    if (value.matches("\\d{2}/\\d{2}/\\d{4}")) {
        try {
            return java.time.LocalDate.parse(
                value,
                java.time.format.DateTimeFormatter
                    .ofPattern("dd/MM/uuuu")
                    .withResolverStyle(
                        java.time.format.ResolverStyle.STRICT
                    )
            ).toString();
        } catch (java.time.format.DateTimeParseException e) {
            return value;
        }
    }

    return value;
}
}
