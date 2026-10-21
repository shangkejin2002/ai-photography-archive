package com.example.usercenter.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.i18n.LocaleContextHolder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class DeepSeekPortfolioIntentClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    public DeepSeekPortfolioIntentClient(
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

    public PortfolioIntentResult parse(String prompt) {
        return parse(prompt, List.of());
    }

    public PortfolioIntentResult parse(String prompt, List<String> availableTags) {
        if (apiKey.isBlank()) throw new IllegalStateException("DeepSeek 尚未配置");
        String outputLanguage = isJapanese() ? "natural Japanese" : "Simplified Chinese";
        String system = """
                You convert a user's request for photos in a private photography archive into JSON.
                Do not write SQL and do not invent unavailable facts. Current date is %s.
                Seasons for this Japan-based archive: spring=Mar-May, summer=Jun-Aug,
                autumn=Sep-Nov, winter=Dec-Feb. Return one JSON object only with keys:
                year(integer|null), season(spring|summer|autumn|winter|null), location(string|null),
                iso(integer|null), favoriteOnly(boolean), cameraModel(string|null), lensModel(string|null),
                subjects(string[]), colors(string[]), visualTags(string[]), excludeTags(string[]),
                diversityRequested(boolean), orientation(landscape|portrait|square|null), limit(integer 1-20),
                shutterSpeed(string|null, canonical example 1/500s), aperture(number|null),
                focalLength(number|null, millimetres), hasPeople(boolean|null), peopleCount(integer|null),
                backgroundColors(string[]), moods(string[]), requiredTags(string[]), preferredTags(string[]),
                sort(relevance|newest|oldest), suggestedTitle(string), suggestedSubtitle(string).
                Write suggestedTitle and suggestedSubtitle in %s, matching the user's current UI language.
                Tags must be concise lowercase English. Prefer terms from the supplied archive vocabulary.
                Put an explicitly mandatory visual property in requiredTags, optional atmosphere/style in
                preferredTags, and negative properties in excludeTags. “有人” means hasPeople=true;
                “不要人物” means hasPeople=false. Background colors belong in backgroundColors, not colors.
                A property inferred from a named location is never mandatory: for example Kinkaku-ji may imply
                temple, architecture or gold, but those belong in preferredTags unless the user explicitly says
                they are required. Preserve the user's location wording when possible.
                Words such as diverse, varied, mixed, variety, 多样, 丰富, 不同内容 describe a selection
                strategy: set diversityRequested=true and NEVER return them as subjects or visualTags.
                A broad scenery/landscape request may use related archive concepts such as nature, outdoor,
                garden, mountain, water, sky, architecture, temple or cityscape. Put negative requests such as
                “不要人物” into excludeTags. Titles should be elegant but factual.
                """.formatted(LocalDate.now(), outputLanguage);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", userContent(prompt, availableTags))
            ));
            body.put("response_format", Map.of("type", "json_object"));
            body.put("thinking", Map.of("type", "disabled"));
            body.put("reasoning_effort", "none");
            body.put("max_tokens", 700);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DeepSeek 返回 HTTP " + response.statusCode());
            }
            String content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content").asText();
            if (content.isBlank()) throw new IllegalStateException("DeepSeek 返回了空结果");
            return parseNode(objectMapper.readTree(content), prompt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI 条件解析已中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("AI 条件解析失败：" + safeMessage(e), e);
        }
    }

    PortfolioIntentResult parseNode(JsonNode node, String prompt) {
        Integer year = nullableInt(node, "year");
        Integer iso = nullableInt(node, "iso");
        int limit = node.path("limit").asInt(20);
        limit = Math.max(1, Math.min(20, limit));
        String season = nullableText(node, "season");
        if (season == null || !List.of("spring", "summer", "autumn", "winter").contains(season)) season = null;
        String sort = nullableText(node, "sort");
        if (sort == null || !List.of("relevance", "newest", "oldest").contains(sort)) sort = "relevance";
        String orientation = nullableText(node, "orientation");
        if (orientation == null || !List.of("landscape", "portrait", "square").contains(orientation)) orientation = null;
        boolean diversityRequested = node.path("diversityRequested").asBoolean(false) || requestsDiversity(prompt);
        String title = text(node, "suggestedTitle", isJapanese() ? "写真 Portfolio" : "摄影作品集");
        String subtitle = text(node, "suggestedSubtitle", prompt);
        return new PortfolioIntentResult(
                year, season, nullableText(node, "location"), iso,
                node.path("favoriteOnly").asBoolean(false),
                nullableText(node, "cameraModel"), nullableText(node, "lensModel"),
                contentStrings(node, "subjects"), strings(node, "colors"), contentStrings(node, "visualTags"),
                contentStrings(node, "excludeTags"), diversityRequested, orientation,
                nullableText(node, "shutterSpeed"), nullableDecimal(node, "aperture"),
                nullableDecimal(node, "focalLength"), nullableBoolean(node, "hasPeople"),
                nullableInt(node, "peopleCount"), strings(node, "backgroundColors"),
                contentStrings(node, "moods"), contentStrings(node, "requiredTags"),
                contentStrings(node, "preferredTags"),
                limit, sort,
                truncate(title, 256), truncate(subtitle, 512)
        );
    }

    private String userContent(String prompt, List<String> availableTags) {
        List<String> vocabulary = availableTags == null ? List.of() : availableTags.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(120)
                .toList();
        if (vocabulary.isEmpty()) return prompt;
        return "Request:\n" + prompt + "\n\nExisting archive tag vocabulary (use when semantically relevant):\n"
                + String.join(", ", vocabulary);
    }

    private List<String> contentStrings(JsonNode node, String field) {
        Set<String> controls = Set.of("diverse", "varied", "variety", "mixed", "different", "diversity",
                "多样", "丰富", "不同", "混合");
        return strings(node, field).stream().filter(value -> !controls.contains(value)).toList();
    }

    private boolean requestsDiversity(String prompt) {
        if (prompt == null) return false;
        String normalized = prompt.toLowerCase(Locale.ROOT);
        return List.of("多样", "丰富", "不同内容", "不同类型", "diverse", "varied", "variety", "mixed")
                .stream().anyMatch(normalized::contains);
    }

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isInt() || value.isLong() ? value.asInt() : null;
    }

    private BigDecimal nullableDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue().stripTrailingZeros() : null;
    }

    private Boolean nullableBoolean(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isBoolean() ? value.asBoolean() : null;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String text = value.asText().trim();
        return text.isBlank() ? null : text;
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = nullableText(node, field);
        return value == null ? fallback : value;
    }

    private List<String> strings(JsonNode node, String field) {
        if (!node.path(field).isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.path(field).forEach(value -> {
            String text = value.asText().trim().toLowerCase(Locale.ROOT);
            if (!text.isBlank() && text.length() <= 128 && !result.contains(text) && result.size() < 10) {
                result.add(text);
            }
        });
        return List.copyOf(result);
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.substring(0, Math.min(message.length(), 240));
    }

    private boolean isJapanese() {
        return "ja".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());
    }
}
