package com.example.usercenter.portfolio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioTechnicalQueryParserTest {

    @Test
    void extractsExactExifConditionsWithoutDependingOnAiWording() {
        PortfolioIntentResult enriched = PortfolioTechnicalQueryParser.enrich(
                "找三张有人、1/500s、f/5.6、35mm、ISO 200 的照片",
                emptyIntent()
        );

        assertThat(enriched.shutterSpeed()).isEqualTo("1/500s");
        assertThat(enriched.aperture()).isEqualByComparingTo("5.6");
        assertThat(enriched.focalLength()).isEqualByComparingTo("35");
        assertThat(enriched.iso()).isEqualTo(200);
        assertThat(enriched.hasPeople()).isTrue();
        assertThat(enriched.limit()).isEqualTo(3);
        assertThat(PortfolioTechnicalQueryParser.exposureSeconds("1/500s"))
                .isEqualByComparingTo(new BigDecimal("0.002"));
    }

    @Test
    void understandsExplicitPeopleExclusion() {
        PortfolioIntentResult enriched = PortfolioTechnicalQueryParser.enrich(
                "选择没有人的安静照片",
                emptyIntent()
        );
        assertThat(enriched.hasPeople()).isFalse();
    }

    @Test
    void extractsJapaneseTechnicalAndVisualConditions() {
        PortfolioIntentResult enriched = PortfolioTechnicalQueryParser.enrich(
                "人物なし、緑の背景、1/500秒、最大8枚",
                emptyIntent()
        );

        assertThat(enriched.hasPeople()).isFalse();
        assertThat(enriched.backgroundColors()).containsExactly("green");
        assertThat(enriched.shutterSpeed()).isEqualTo("1/500s");
        assertThat(enriched.limit()).isEqualTo(8);
    }

    @Test
    void downgradesAiInferredRequiredTagsWhenUserDidNotSayMust() {
        PortfolioIntentResult source = intentWithRequiredTag("temple");
        PortfolioIntentResult enriched = PortfolioTechnicalQueryParser.enrich(
                "今年秋天在金阁寺拍摄的照片", source
        );

        assertThat(enriched.requiredTags()).isEmpty();
        assertThat(enriched.preferredTags()).contains("temple");
    }

    @Test
    void keepsRequiredTagsWhenUserExplicitlySaysMust() {
        PortfolioIntentResult enriched = PortfolioTechnicalQueryParser.enrich(
                "找三张必须包含寺庙的照片", intentWithRequiredTag("temple")
        );

        assertThat(enriched.requiredTags()).containsExactly("temple");
    }

    private PortfolioIntentResult emptyIntent() {
        return new PortfolioIntentResult(
                null, null, null, null, false, null, null,
                List.of(), List.of(), List.of(), List.of(), false, null,
                null, null, null, null, null, List.of(), List.of(), List.of(), List.of(),
                20, "relevance", "作品集", "测试"
        );
    }

    private PortfolioIntentResult intentWithRequiredTag(String tag) {
        PortfolioIntentResult empty = emptyIntent();
        return new PortfolioIntentResult(
                empty.year(), empty.season(), "Kinkaku-ji", empty.iso(), empty.favoriteOnly(),
                empty.cameraModel(), empty.lensModel(), empty.subjects(), empty.colors(), empty.visualTags(),
                empty.excludeTags(), empty.diversityRequested(), empty.orientation(), empty.shutterSpeed(),
                empty.aperture(), empty.focalLength(), empty.hasPeople(), empty.peopleCount(),
                empty.backgroundColors(), empty.moods(), List.of(tag), List.of(), empty.limit(), empty.sort(),
                empty.suggestedTitle(), empty.suggestedSubtitle()
        );
    }
}
