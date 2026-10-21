package com.example.usercenter.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekPhotoAnalysisClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesAndNormalizesStructuredVisualIndex() throws Exception {
        DeepSeekPhotoAnalysisClient client = new DeepSeekPhotoAnalysisClient(
                objectMapper, "test-key", "https://api.deepseek.com/", "deepseek-flash", 60
        );

        PhotoAnalysisResult result = client.parseResult(objectMapper.readTree("""
                {
                  "scenes": ["Temple", " temple ", "Landscape"],
                  "subjects": ["Golden Pavilion"],
                  "visualElements": ["Maple Leaves"],
                  "colors": ["Gold", "Blue"],
                  "lighting": ["Daylight"],
                  "weather": ["Clear"],
                  "compositions": ["Natural Framing"],
                  "visualSeasons": ["Autumn"],
                  "dominantColors": ["Green", "Gold"],
                  "backgroundColors": ["Green"],
                  "foregroundColors": ["Gold"],
                  "moods": ["Calm"],
                  "activities": ["Walking"],
                  "depthCues": ["Layered"],
                  "peoplePresent": true,
                  "peopleCount": 2,
                  "peopleProminence": "background",
                  "tags": ["architecture"],
                  "descriptionZh": "  金色建筑被秋叶与蓝天环绕。  ",
                  "descriptionJa": "  金色の建物を紅葉と青空が囲んでいます。  "
                }
                """));

        assertThat(result.scenes()).containsExactly("temple", "landscape");
        assertThat(result.subjects()).containsExactly("golden pavilion");
        assertThat(result.visualElements()).containsExactly("maple leaves");
        assertThat(result.compositions()).containsExactly("natural framing");
        assertThat(result.backgroundColors()).containsExactly("green");
        assertThat(result.moods()).containsExactly("calm");
        assertThat(result.peoplePresent()).isTrue();
        assertThat(result.peopleCount()).isEqualTo(2);
        assertThat(result.peopleProminence()).isEqualTo("background");
        assertThat(result.description()).isEqualTo("金色建筑被秋叶与蓝天环绕。");
        assertThat(result.descriptionJa()).isEqualTo("金色の建物を紅葉と青空が囲んでいます。");
    }

    @Test
    void acceptsLegacyChineseDescriptionWithoutInventingJapaneseText() throws Exception {
        DeepSeekPhotoAnalysisClient client = new DeepSeekPhotoAnalysisClient(
                objectMapper, "test-key", "https://api.deepseek.com/", "deepseek-flash", 60
        );

        PhotoAnalysisResult result = client.parseResult(objectMapper.readTree("""
                {"description":"雪中的传统建筑入口。"}
                """));

        assertThat(result.description()).isEqualTo("雪中的传统建筑入口。");
        assertThat(result.descriptionJa()).isEmpty();
    }
}
