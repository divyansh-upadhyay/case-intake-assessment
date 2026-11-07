package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReviewControllerTest {

    private final ReviewController controller = new ReviewController();
    private final ObjectMapper mapper = new ObjectMapper();

    private final Path document = Path.of("documents", "D99.txt");
    private final Path reviewFile = Path.of("reviewed-cases", "D99.json");

    @BeforeEach
    void setup() throws Exception {
        Files.createDirectories(document.getParent());
        Files.createDirectories(reviewFile.getParent());

        Files.writeString(document, "Test adverse event report");
        Files.deleteIfExists(reviewFile);
    }

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(document);
        Files.deleteIfExists(reviewFile);
    }

    @Test
    void shouldReturnEmptyReviewWhenNoReviewExists() throws Exception {
        JsonNode result = controller.getReview("D99");

        assertEquals("D99", result.get("documentId").asText());
        assertTrue(result.get("corrections").isObject());
        assertEquals(0, result.get("corrections").size());
    }

    @Test
    void shouldSaveReviewSuccessfully() throws Exception {
        JsonNode request = mapper.readTree("""
            {
              "corrections": {
                "0": {
                  "patient_age_years": {
                    "value": 45,
                    "reviewed": true,
                    "note": "Verified against source"
                  }
                }
              }
            }
            """);

        JsonNode result = controller.saveReview("D99", request);

        assertEquals("D99", result.get("documentId").asText());
        assertTrue(Files.exists(reviewFile));

        assertEquals(
            45,
            result.path("corrections")
                  .path("0")
                  .path("patient_age_years")
                  .path("value")
                  .asInt()
        );
    }

    @Test
    void shouldRetrieveSavedReview() throws Exception {
        JsonNode request = mapper.readTree("""
            {
              "corrections": {
                "0": {
                  "patient_age_years": {
                    "value": 45,
                    "reviewed": true,
                    "note": "Corrected by reviewer"
                  }
                }
              }
            }
            """);

        controller.saveReview("D99", request);

        JsonNode result = controller.getReview("D99");

        assertEquals(
            45,
            result.path("corrections")
                  .path("0")
                  .path("patient_age_years")
                  .path("value")
                  .asInt()
        );

        assertEquals(
            "Corrected by reviewer",
            result.path("corrections")
                  .path("0")
                  .path("patient_age_years")
                  .path("note")
                  .asText()
        );
    }

    @Test
    void shouldUpdateExistingCorrection() throws Exception {
        JsonNode first = mapper.readTree("""
            {
              "corrections": {
                "0": {
                  "patient_age_years": {
                    "value": 50,
                    "reviewed": true,
                    "note": "Initial review"
                  }
                }
              }
            }
            """);

        JsonNode second = mapper.readTree("""
            {
              "corrections": {
                "0": {
                  "patient_age_years": {
                    "value": 45,
                    "reviewed": true,
                    "note": "Corrected after verification"
                  }
                }
              }
            }
            """);

        controller.saveReview("D99", first);
        controller.saveReview("D99", second);

        JsonNode result = controller.getReview("D99");

        assertEquals(
            45,
            result.path("corrections")
                  .path("0")
                  .path("patient_age_years")
                  .path("value")
                  .asInt()
        );
    }

    @Test
    void shouldRejectMissingCorrections() throws Exception {
        JsonNode invalidRequest = mapper.readTree("""
            {
              "documentId": "D99"
            }
            """);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.saveReview("D99", invalidRequest)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldRejectInvalidDocumentId() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.getReview("../D99")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldRejectNonexistentDocument() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.getReview("D98")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
