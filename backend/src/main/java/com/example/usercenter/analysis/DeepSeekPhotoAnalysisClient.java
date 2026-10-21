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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DeepSeekPhotoAnalysisClient {

    private static final String SYSTEM_PROMPT = """
            You create a reusable visual-semantic index for a private photography archive.
            Return one JSON object only. Do not infer an exact place, person identity, camera data,
            date, or season when it is not visually reliable. Tags must be concise lowercase English,
            chosen from common photographic vocabulary whenever possible. descriptionZh must be one concise
            Simplified Chinese sentence and descriptionJa must be one concise, natural Japanese sentence.
            Describe only visible content. Use an empty array when uncertain.
            Required keys: scenes, subjects, visualElements, colors, lighting, weather,
            compositions, visualSeasons, dominantColors, backgroundColors, foregroundColors,
            moods, activities, depthCues, peoplePresent, peopleCount, peopleProminence, tags,
            descriptionZh, descriptionJa.
            peoplePresent must be true only when at least one person is visible; peopleCount is an integer
            when reasonably countable, otherwise null. peopleProminence is main|secondary|background|null.
            dominantColors are ordered by estimated visual coverage. backgroundColors and foregroundColors
            describe spatial role, not merely any color present. moods describe visible atmosphere only.
            Each array must contain at most 8 distinct strings. Never include GPS or EXIF guesses.
            """;
    private static final int MAX_TAGS_PER_GROUP = 8;
    private static final int MAX_TAG_LENGTH = 128;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    public DeepSeekPhotoAnalysisClient(
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

    public String model() {
        return model;
    }

    public PhotoAnalysisResult analyze(byte[] thumbnailJpeg) {
        if (!isConfigured()) {
            throw new IllegalStateException("DeepSeek API key is not configured");
        }
        try {
            Map<String, Object> image = Map.of(
                    "type", "image_url",
                    "image_url", Map.of(
                            "url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(thumbnailJpeg),
                            "detail", "low"
                    )
            );
            Map<String, Object> text = Map.of(
                    "type", "text",
                    "text", "Analyze this photograph for archive search. Follow the system schema exactly."
            );
            List<Map<String, Object>> messages = List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", List.of(text, image))
            );
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("response_format", Map.of("type", "json_object"));
            body.put("reasoning_effort", "none");
            body.put("temperature", 0.2);
            body.put("max_tokens", 800);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DeepSeek request failed with HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content.isBlank()) {
                throw new IllegalStateException("DeepSeek returned an empty analysis");
            }
            return parseResult(objectMapper.readTree(content));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI analysis was interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("AI analysis request failed: " + safeMessage(e), e);
        }
    }

    PhotoAnalysisResult parseResult(JsonNode node) {
        return new PhotoAnalysisResult(
                strings(node, "scenes"),
                strings(node, "subjects"),
                strings(node, "visualElements"),
                strings(node, "colors"),
                strings(node, "lighting"),
                strings(node, "weather"),
                strings(node, "compositions"),
                strings(node, "visualSeasons"),
                strings(node, "dominantColors"),
                strings(node, "backgroundColors"),
                strings(node, "foregroundColors"),
                strings(node, "moods"),
                strings(node, "activities"),
                strings(node, "depthCues"),
                nullableBoolean(node, "peoplePresent"),
                nullableInteger(node, "peopleCount"),
                nullableText(node, "peopleProminence"),
                strings(node, "tags"),
                cleanDescription(node.hasNonNull("descriptionZh")
                        ? node.path("descriptionZh").asText()
                        : node.path("description").asText()),
                cleanDescription(node.path("descriptionJa").asText())
        );
    }

    private Boolean nullableBoolean(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isBoolean() ? value.asBoolean() : null;
    }

    private Integer nullableInteger(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber()) return null;
        int count = value.asInt();
        return count >= 0 && count <= 1000 ? count : null;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) return null;
        String normalized = value.asText().trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() || normalized.length() > MAX_TAG_LENGTH ? null : normalized;
    }

    private List<String> strings(JsonNode node, String field) {
        JsonNode values = node.path(field);
        if (!values.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            String normalized = value.asText().trim().toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", " ");
            if (!normalized.isBlank() && normalized.length() <= MAX_TAG_LENGTH && !result.contains(normalized)) {
                result.add(normalized);
            }
            if (result.size() >= MAX_TAGS_PER_GROUP) break;
        }
        return List.copyOf(result);
    }

    private String cleanDescription(String value) {
        String cleaned = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (cleaned.length() > 1000) return cleaned.substring(0, 1000);
        return cleaned;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() > 240 ? message.substring(0, 240) : message;
    }
}
