package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.*;
import java.util.*;

public class ReplayRunner {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        Path catalogPath = Path.of(
                args.length > 0 ? args[0] : "catalog/field_catalog.json"
        );

        JsonNode catalog = mapper.readTree(catalogPath.toFile());

        Path fixtureDir = Path.of("fixtures");
        Path outputDir = Path.of("out");

        Files.createDirectories(outputDir);

        int processed = 0;
        int missing = 0;

        for (int i = 1; i <= 8; i++) {

            String documentId = String.format("D%02d", i);

            Path fixture = fixtureDir.resolve(documentId + ".json");
            Path output = outputDir.resolve(documentId + ".json");

            if (!Files.exists(fixture)) {
                System.out.println(
                        "MISSING FIXTURE: " + documentId
                );
                missing++;
                continue;
            }

            JsonNode root = mapper.readTree(fixture.toFile());

            if (!root.isObject()) {
                throw new IllegalArgumentException(
                        "Invalid fixture: " + fixture
                );
            }

            ObjectNode extraction = (ObjectNode) root;

            if (!documentId.equals(
                    extraction.path("document_id").asText())) {
                throw new IllegalArgumentException(
                        "Fixture document ID mismatch: " + documentId
                );
            }

            extraction.put(
                    "catalog_version",
                    catalog.path("catalog_version").asText()
            );

            for (JsonNode caseNode : extraction.path("cases")) {

                ObjectNode fields =
                        (ObjectNode) caseNode.path("fields");

                for (JsonNode catalogField : catalog.path("fields")) {

                    String fieldId =
                            catalogField.path("id").asText();

                    if (!fields.has(fieldId)) {

                        ObjectNode unknown =
                                mapper.createObjectNode();

                        unknown.put("status", "unknown");
                        unknown.put(
                                "note",
                                "Field not present in recorded fixture"
                        );

                        fields.set(fieldId, unknown);
                    }
                }
            }

            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(output.toFile(), extraction);

            System.out.println("REPLAYED: " + documentId);

            processed++;
        }

        System.out.println();
        System.out.println("Replay completed");
        System.out.println("Processed: " + processed);
        System.out.println("Missing fixtures: " + missing);

        if (missing > 0) {
            System.exit(1);
        }
    }
}
