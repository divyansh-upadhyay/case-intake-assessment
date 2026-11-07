package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;

public class QualityCheckRunner {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            throw new IllegalArgumentException(
                    "Provide a document ID, e.g. D03"
            );
        }

        String documentId = args[0];

        if (!documentId.matches("D\\d{2}")) {
            throw new IllegalArgumentException("Invalid document ID");
        }

        String document = Files.readString(
                Path.of("documents", documentId + ".txt")
        );

        ObjectNode result = (ObjectNode) mapper.readTree(
                Files.readString(
                        Path.of("out-hybrid", documentId + ".json")
                )
        );

        ArrayNode cases = (ArrayNode) result.path("cases");

        for (JsonNode item : cases) {

            ObjectNode fields = (ObjectNode) item.path("fields");

            ExtractionQualityChecks.checkAgeConflict(
                    fields,
                    document
            );

            ExtractionQualityChecks.checkEventTerms(fields);
        }

        Path outputDir = Path.of("out-quality");
        Files.createDirectories(outputDir);

        Path output = outputDir.resolve(documentId + ".json");

        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), result);

        System.out.println("Quality checks completed: " + output);
    }
}
