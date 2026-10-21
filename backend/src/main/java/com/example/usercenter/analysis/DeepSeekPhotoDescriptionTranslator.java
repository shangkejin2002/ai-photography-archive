package com.example.usercenter.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekPhotoDescriptionTranslator {

    private static final String SYSTEM_PROMPT = """
            Translate the supplied Chinese photography descriptions into concise, natural Japanese suitable
            for a Japanese photography archive. Preserve only the visible facts in the source. Do not add
            place names, identities, dates, camera data, or explanations. Return JSON only in this shape:
            {"translations":[{"id":1,"descriptionJa":"..."}]}.
            """;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    public DeepSeekPhotoDescriptionTranslator(
            ObjectMapper objectMapper,
            @Value("${app.ai.deepseek.api-key:}") String apiKey,
            @Value("${app.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${app.ai.deepseek.model:deepseek-flash}") String model,
            @Value("${app.ai.deepseek.timeout-seconds:60}") long timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.timeout = Duration.ofSeconds(Math.max(10, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public Map<Long, String> translate(Map<Long, String> descriptions) {
        if (descriptions.isEmpty()) return Map.of();
        if (!isConfigured()) throw new IllegalStateException("DeepSeek API key is not configured");
        try {
            List<Map<String, Object>> items = descriptions.entrySet().stream()
                    .map(entry -> Map.<String, Object>of("id", entry.getKey(), "descriptionZh", entry.getValue()))
                    .toList();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", objectMapper.writeValueAsString(items))
            ));
            body.put("response_format", Map.of("type", "json_object"));
            body.put("reasoning_effort", "none");
            body.put("temperature", 0.1);
            body.put("max_tokens", Math.max(400, descriptions.size() * 100));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DeepSeek translation failed with HTTP " + response.statusCode());
            }
            String content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content").asText();
            return parseTranslations(objectMapper.readTree(content));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI translation was interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("AI translation request failed", e);
        }
    }

    Map<Long, String> parseTranslations(JsonNode root) {
        Map<Long, String> result = new LinkedHashMap<>();
        JsonNode values = root.path("translations");
        if (!values.isArray()) return result;
        for (JsonNode value : values) {
            long id = value.path("id").asLong(0);
            String description = value.path("descriptionJa").asText("").trim().replaceAll("\\s+", " ");
            if (id > 0 && !description.isBlank()) {
                result.put(id, description.length() > 1000 ? description.substring(0, 1000) : description);
            }
        }
        return Map.copyOf(result);
    }
}
