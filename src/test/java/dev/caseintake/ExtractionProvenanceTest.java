package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtractionProvenanceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SOURCE = "Report: Patient developed a rash after treatment.\n";

    private JsonNode catalogField(String type) throws Exception {
        return MAPPER.readTree("{\"id\":\"event_outcome\",\"type\":\"" + type + "\",\"validation\":{}}");
    }

    private ObjectNode candidate(String value, String quote) {
        ObjectNode candidate = MAPPER.createObjectNode();
        candidate.put("status", "extracted");
        candidate.put("value", value);
        candidate.put("confidence", "high");
        candidate.putArray("provenance").addObject().put("quote", quote);
        return candidate;
    }

    @Test
    void groundedQuoteHasExactOffsets() throws Exception {
        ObjectNode result = ExtractionService.validateField(
                candidate("rash", "developed a rash"), catalogField("string"), SOURCE);
        assertEquals("extracted", result.path("status").asText());
        JsonNode evidence = result.path("provenance").get(0);
        int start = evidence.path("start").asInt();
        int end = evidence.path("end").asInt();
        assertEquals("developed a rash", SOURCE.substring(start, end));
    }

    @Test
    void unsupportedEvidenceIsRejected() throws Exception {
        ObjectNode result = ExtractionService.validateField(
                candidate("rash", "not present in report"), catalogField("string"), SOURCE);
        assertEquals("unknown", result.path("status").asText());
    }

    @Test
    void extractedFieldWithoutEvidenceIsRejected() throws Exception {
        ObjectNode candidate = candidate("rash", "developed a rash");
        candidate.remove("provenance");
        ObjectNode result = ExtractionService.validateField(candidate, catalogField("string"), SOURCE);
        assertEquals("unknown", result.path("status").asText());
    }

    @Test
    void unknownFieldRemainsUnknown() throws Exception {
        ObjectNode candidate = MAPPER.createObjectNode();
        candidate.put("status", "unknown");
        candidate.put("note", "Not in source");
        ObjectNode result = ExtractionService.validateField(candidate, catalogField("string"), SOURCE);
        assertEquals("unknown", result.path("status").asText());
    }

    @Test
    void conflictIsPreserved() throws Exception {
        ObjectNode candidate = MAPPER.createObjectNode();
        candidate.put("status", "conflict");
        candidate.put("note", "Contradictory source values");
        ObjectNode result = ExtractionService.validateField(candidate, catalogField("string"), SOURCE);
        assertEquals("conflict", result.path("status").asText());
    }

    @Test
    void unicodeEvidenceUsesCodePointOffsets() throws Exception {
        String source = "🧪 Patient has rash";
        ObjectNode result = ExtractionService.validateField(
                candidate("rash", "Patient has rash"), catalogField("string"), source);
        assertEquals("extracted", result.path("status").asText());
        JsonNode evidence = result.path("provenance").get(0);
        int startCp = evidence.path("start").asInt();
        int endCp = evidence.path("end").asInt();
        int startUtf16 = source.offsetByCodePoints(0, startCp);
        int endUtf16 = source.offsetByCodePoints(0, endCp);
        assertEquals("Patient has rash", source.substring(startUtf16, endUtf16));
    }
}
