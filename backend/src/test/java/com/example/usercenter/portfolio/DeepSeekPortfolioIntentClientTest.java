package com.example.usercenter.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekPortfolioIntentClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesAndNormalizesPortfolioIntent() throws Exception {
        DeepSeekPortfolioIntentClient client = new DeepSeekPortfolioIntentClient(
                objectMapper, "test-key", "https://api.deepseek.com/", "deepseek-flash", 60
        );

        PortfolioIntentResult result = client.parseNode(objectMapper.readTree("""
                {
                  "year": 2026,
                  "season": "autumn",
                  "location": "金阁寺",
                  "iso": 200,
                  "favoriteOnly": true,
                  "cameraModel": "E-P7",
                  "lensModel": null,
                  "subjects": ["Temple", " temple "],
                  "colors": ["Gold", "Green"],
                  "visualTags": ["Architecture"],
                  "shutterSpeed": "1/500s",
                  "aperture": 5.6,
                  "focalLength": 35,
                  "hasPeople": false,
                  "backgroundColors": ["Green"],
                  "moods": ["Calm"],
                  "requiredTags": ["temple"],
                  "preferredTags": ["reflection"],
                  "limit": 50,
                  "sort": "relevance",
                  "suggestedTitle": "金色秋日",
                  "suggestedSubtitle": "金阁寺摄影选集"
                }
                """), "秋天的金阁寺");

        assertThat(result.year()).isEqualTo(2026);
        assertThat(result.season()).isEqualTo("autumn");
        assertThat(result.location()).isEqualTo("金阁寺");
        assertThat(result.iso()).isEqualTo(200);
        assertThat(result.favoriteOnly()).isTrue();
        assertThat(result.subjects()).containsExactly("temple");
        assertThat(result.colors()).containsExactly("gold", "green");
        assertThat(result.visualTags()).containsExactly("architecture");
        assertThat(result.shutterSpeed()).isEqualTo("1/500s");
        assertThat(result.aperture()).isEqualByComparingTo("5.6");
        assertThat(result.focalLength()).isEqualByComparingTo("35");
        assertThat(result.hasPeople()).isFalse();
        assertThat(result.backgroundColors()).containsExactly("green");
        assertThat(result.requiredTags()).containsExactly("temple");
        assertThat(result.limit()).isEqualTo(20);
        assertThat(result.suggestedTitle()).isEqualTo("金色秋日");
    }

    @Test
    void separatesDiversityStrategyFromContentTags() throws Exception {
        DeepSeekPortfolioIntentClient client = new DeepSeekPortfolioIntentClient(
                objectMapper, "test-key", "https://api.deepseek.com/", "deepseek-flash", 60
        );

        PortfolioIntentResult result = client.parseNode(objectMapper.readTree("""
                {
                  "subjects": ["landscape", "varied"],
                  "colors": [],
                  "visualTags": ["scenery", "diverse"],
                  "excludeTags": ["people"],
                  "diversityRequested": false,
                  "orientation": "landscape",
                  "limit": 5
                }
                """), "请做五张内容多样的景色合集，不要人物");

        assertThat(result.subjects()).containsExactly("landscape");
        assertThat(result.visualTags()).containsExactly("scenery");
        assertThat(result.excludeTags()).containsExactly("people");
        assertThat(result.diversityRequested()).isTrue();
        assertThat(result.orientation()).isEqualTo("landscape");
        assertThat(result.limit()).isEqualTo(5);
    }
}
