package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldNormalizeAge() throws Exception {
        JsonNode field = mapper.readTree("""
            {"id":"patient_age_years","type":"integer"}
            """);

        JsonNode result = FieldNormalizer.normalize(
                mapper.getNodeFactory().textNode("67"),
                field
        );

        assertEquals(67, result.asInt());
        assertTrue(result.isIntegralNumber());
    }

    @Test
    void shouldNormalizeInitials() throws Exception {
        JsonNode field = mapper.readTree("""
            {"id":"patient_initials","type":"string"}
            """);

        JsonNode result = FieldNormalizer.normalize(
                mapper.getNodeFactory().textNode("J.T.O."),
                field
        );

        assertEquals("JTO", result.asText());
    }

    @Test
    void shouldNormalizeDate() throws Exception {
        JsonNode field = mapper.readTree("""
            {"id":"suspect_drug_start_date","type":"date"}
            """);

        JsonNode result = FieldNormalizer.normalize(
                mapper.getNodeFactory().textNode("20 November 2025"),
                field
        );

        assertEquals("2025-11-20", result.asText());
    }

    @Test
    void shouldNormalizeCodedValue() throws Exception {
        JsonNode field = mapper.readTree("""
            {
              "id":"patient_sex",
              "type":"coded",
              "options":[
                {"code":"male","label":"Male"},
                {"code":"female","label":"Female"}
              ]
            }
            """);

        JsonNode result = FieldNormalizer.normalize(
                mapper.getNodeFactory().textNode("Male"),
                field
        );

        assertEquals("male", result.asText());
    }

    @Test
    void shouldNormalizeDoseValue() throws Exception {
        JsonNode field = mapper.readTree("""
            {"id":"suspect_drug_dose_value","type":"number"}
            """);

        JsonNode result = FieldNormalizer.normalize(
                mapper.getNodeFactory().textNode("40 mg"),
                field
        );

        assertTrue(result.isNumber());
        assertEquals(40.0, result.asDouble());
    }

    @Test
    void shouldNotInventMissingValues() throws Exception {
        JsonNode field = mapper.readTree("""
            {"id":"patient_age_years","type":"integer"}
            """);

        JsonNode result = FieldNormalizer.normalize(
                mapper.nullNode(),
                field
        );

        assertTrue(result.isNull());
    }
    @Test
    void normalizesDoseWithAdministrationInstructions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode field = mapper.readTree("""
        {
          "id": "suspect_drug_dose_value",
          "type": "number"
        }
        """);

        JsonNode input = mapper.getNodeFactory()
                .textNode("40 mg orally once daily");

        JsonNode result = FieldNormalizer.normalize(input, field);

        assertTrue(result.isNumber());
        assertEquals(40.0, result.asDouble());
    }
@Test
void shouldNormalizeSlashSeparatedDates() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    JsonNode field = mapper.readTree("""
        {
          "id": "event_onset_date",
          "type": "date"
        }
        """);

    JsonNode value = mapper.getNodeFactory()
            .textNode("28/04/2026");

    JsonNode normalized = FieldNormalizer.normalize(value, field);

    assertEquals("2026-04-28", normalized.asText());
}
}
