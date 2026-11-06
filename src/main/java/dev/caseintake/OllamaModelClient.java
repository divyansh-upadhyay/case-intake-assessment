package dev.caseintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class OllamaModelClient implements ModelClient {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String endpoint;
    private final String model;

    public OllamaModelClient() {
        this.endpoint = System.getenv().getOrDefault(
                "OLLAMA_URL",
                "http://localhost:11434/api/generate"
        );

        this.model = System.getenv().getOrDefault(
                "OLLAMA_MODEL",
                "qwen2.5:3b"
        );
    }

    @Override
    public String extract(String prompt) throws Exception {

        String body = mapper.writeValueAsString(Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "format", "json"
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Ollama HTTP " + response.statusCode()
                            + ": " + response.body()
            );
        }

        JsonNode result = mapper.readTree(response.body());

        if (!result.hasNonNull("response")) {
            throw new IllegalStateException(
                    "Ollama response does not contain generated text"
            );
        }

        return result.get("response").asText();
    }
}