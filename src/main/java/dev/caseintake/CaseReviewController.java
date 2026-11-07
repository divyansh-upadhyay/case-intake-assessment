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

@RestController
@RequestMapping("/api/cases")
@CrossOrigin(origins = "http://localhost:5173")
public class CaseReviewController {

    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/{documentId}")
    public JsonNode getCase(@PathVariable String documentId)
            throws IOException {

        validateId(documentId);

        Path sourcePath = Path.of(
                "documents", documentId + ".txt"
        );

        Path extractionPath = Path.of(
                "out-recovered", documentId + ".json"
        );

        if (!Files.isRegularFile(sourcePath)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Source document not found"
            );
        }

        if (!Files.isRegularFile(extractionPath)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Extraction result not found"
            );
        }

        ObjectNode response = mapper.createObjectNode();

        response.put("documentId", documentId);
        response.put("sourceText", Files.readString(sourcePath));

        response.set(
                "extraction",
                mapper.readTree(extractionPath.toFile())
        );

        return response;
    }

    private void validateId(String documentId) {
        if (!documentId.matches("D[0-9]{2}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid document ID"
            );
        }
    }
}
