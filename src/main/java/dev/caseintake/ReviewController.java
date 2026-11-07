package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/cases")
@CrossOrigin(originPatterns = {
        "http://localhost:517*",
        "http://127.0.0.1:517*"
})
public class ReviewController {

    private final ObjectMapper mapper = new ObjectMapper();

    private void validateDocumentId(String id) {
        if (!id.matches("D[0-9]{2}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid document ID"
            );
        }

        if (!Files.isRegularFile(Path.of("documents", id + ".txt"))) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found"
            );
        }
    }

    @GetMapping("/{documentId}/review")
    public JsonNode getReview(@PathVariable String documentId)
            throws IOException {

        validateDocumentId(documentId);

        Path file = Path.of("reviewed-cases", documentId + ".json");

        if (!Files.exists(file)) {
            ObjectNode response = mapper.createObjectNode();
            response.put("documentId", documentId);
            response.set("corrections", mapper.createObjectNode());
            return response;
        }

        return mapper.readTree(file.toFile());
    }

    @PutMapping("/{documentId}/review")
    public JsonNode saveReview(
            @PathVariable String documentId,
            @RequestBody JsonNode request
    ) throws IOException {

        validateDocumentId(documentId);

        if (!request.isObject()
                || !request.has("corrections")
                || !request.get("corrections").isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "corrections must be a JSON object"
            );
        }

        Path directory = Path.of("reviewed-cases");
        Files.createDirectories(directory);

        Path target = directory.resolve(documentId + ".json");

        ObjectNode response = mapper.createObjectNode();
        response.put("documentId", documentId);
        response.put("updatedAt", Instant.now().toString());
        response.set("corrections", request.get("corrections"));

        Path temp = Files.createTempFile(directory, documentId, ".tmp");

        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(temp.toFile(), response);

            Files.move(
                    temp,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } finally {
            Files.deleteIfExists(temp);
        }

        return response;
    }
}
