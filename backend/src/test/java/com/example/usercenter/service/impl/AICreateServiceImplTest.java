package com.example.usercenter.service.impl;

import com.example.usercenter.model.vo.PhotoTagVO;
import com.example.usercenter.model.vo.PhotoVO;
import com.example.usercenter.portfolio.DeepSeekPortfolioIntentClient;
import com.example.usercenter.i18n.AppMessages;
import org.springframework.context.support.ResourceBundleMessageSource;
import com.example.usercenter.portfolio.PortfolioIntentResult;
import com.example.usercenter.service.PhotoService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AICreateServiceImplTest {

    @Test
    void broadSceneryRequestMatchesRelatedArchiveTagsAndDiversifiesResults() {
        DeepSeekPortfolioIntentClient intentClient = mock(DeepSeekPortfolioIntentClient.class);
        PhotoService photoService = mock(PhotoService.class);
        AICreateServiceImpl service = new AICreateServiceImpl(intentClient, photoService, messages());

        PortfolioIntentResult intent = new PortfolioIntentResult(
                null, null, null, null, false, null, null,
                List.of("landscape"), List.of(), List.of(), List.of("people"),
                true, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                5, "relevance", "五景", "内容多样的景色合集"
        );
        when(intentClient.parse(anyString(), anyList())).thenReturn(intent);
        when(photoService.listOwned(7L, null)).thenReturn(List.of(
                photo(1L, "garden", "outdoor"),
                photo(2L, "temple", "architecture"),
                photo(3L, "portrait", "people")
        ));

        var preview = service.preview(7L, "请帮我做一个五张照片的景色合集，要求照片内容多样");

        assertThat(preview.candidates()).extracting(candidate -> candidate.photo().id())
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(preview.intent().diversityRequested()).isTrue();
    }

    @Test
    void combinesLocalExifPeopleAndBackgroundColorConditions() {
        DeepSeekPortfolioIntentClient intentClient = mock(DeepSeekPortfolioIntentClient.class);
        PhotoService photoService = mock(PhotoService.class);
        AICreateServiceImpl service = new AICreateServiceImpl(intentClient, photoService, messages());
        PortfolioIntentResult intent = new PortfolioIntentResult(
                null, null, null, null, false, null, null,
                List.of(), List.of(), List.of(), List.of(), false, null,
                null, null, null, null, null, List.of("green"), List.of(), List.of(), List.of(),
                20, "relevance", "绿色人物", "测试"
        );
        when(intentClient.parse(anyString(), anyList())).thenReturn(intent);
        when(photoService.listOwned(7L, null)).thenReturn(List.of(
                photoWithMetadata(1L, "1/500s", new PhotoTagVO("present", "people_presence", BigDecimal.ONE),
                        new PhotoTagVO("green", "background_color", new BigDecimal("0.9"))),
                photoWithMetadata(2L, "1/640s", new PhotoTagVO("present", "people_presence", BigDecimal.ONE),
                        new PhotoTagVO("green", "background_color", new BigDecimal("0.9"))),
                photoWithMetadata(3L, "1/500s", new PhotoTagVO("green", "background_color", new BigDecimal("0.9")))
        ));

        var preview = service.preview(7L, "找一张1/500s、有人、背景绿色的照片");

        assertThat(preview.candidates()).extracting(candidate -> candidate.photo().id()).containsExactly(1L);
        assertThat(preview.intent().shutterSpeed()).isEqualTo("1/500s");
        assertThat(preview.intent().hasPeople()).isTrue();
        assertThat(preview.intent().limit()).isEqualTo(1);
    }

    @Test
    void matchesRomanizedAiLocationAgainstChineseStoredLocationAndSoftensInferredTag() {
        DeepSeekPortfolioIntentClient intentClient = mock(DeepSeekPortfolioIntentClient.class);
        PhotoService photoService = mock(PhotoService.class);
        AICreateServiceImpl service = new AICreateServiceImpl(intentClient, photoService, messages());
        PortfolioIntentResult intent = new PortfolioIntentResult(
                2026, "autumn", "Kinkaku-ji", null, false, null, null,
                List.of(), List.of("gold"), List.of(), List.of(), false, null,
                null, null, null, null, null, List.of(), List.of(), List.of("temple"), List.of("gold"),
                3, "relevance", "金阁寺秋景", "测试"
        );
        when(intentClient.parse(anyString(), anyList())).thenReturn(intent);
        when(photoService.listOwned(7L, null)).thenReturn(List.of(kinkakujiPhoto()));

        var preview = service.preview(7L, "帮我找出今年秋天在金阁寺拍摄的照片，要3张");

        assertThat(preview.candidates()).extracting(candidate -> candidate.photo().id()).containsExactly(9L);
        assertThat(preview.intent().requiredTags()).isEmpty();
        assertThat(preview.intent().preferredTags()).contains("temple", "gold");
    }

    private PhotoVO photo(long id, String... tags) {
        List<PhotoTagVO> aiTags = java.util.Arrays.stream(tags)
                .map(tag -> new PhotoTagVO(tag, "scene", BigDecimal.ONE))
                .toList();
        LocalDateTime now = LocalDateTime.of(2026, 10, 3, 10, 0);
        return new PhotoVO(
                id,
                "photo-" + id + ".jpg",
                "/original/" + id,
                "/thumb/" + id,
                "image/jpeg",
                1024L,
                1600,
                1200,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "COMPLETED",
                null,
                "An archive photograph",
                null,
                aiTags,
                "COMPLETED",
                null,
                "deepseek",
                "1",
                1,
                now,
                now,
                now
        );
    }

    private PhotoVO photoWithMetadata(long id, String shutter, PhotoTagVO... tags) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 3, 10, 0);
        return new PhotoVO(
                id, "photo-" + id + ".jpg", "/original/" + id, "/thumb/" + id,
                "image/jpeg", 1024L, 1600, 1200, false,
                null, null, null, new BigDecimal("35"), 70, new BigDecimal("5.6"), shutter, 200,
                now, null, null, null, null, null, null, null, null,
                "COMPLETED", null, "An archive photograph", null, List.of(tags), "COMPLETED", null,
                "deepseek", "stage5-v2", 1, now, now, now
        );
    }

    private PhotoVO kinkakujiPhoto() {
        LocalDateTime takenAt = LocalDateTime.of(2026, 9, 22, 10, 47);
        return new PhotoVO(
                9L, "kinkakuji.jpg", "/original/9", "/thumb/9", "image/jpeg", 1024L,
                1600, 1200, false, "OLYMPUS", "E-P7", "LUMIX", new BigDecimal("35"),
                70, new BigDecimal("5.6"), "1/640s", 200, takenAt, null, null, null, null, null,
                new BigDecimal("35.039514"), new BigDecimal("135.728442"), "金阁寺，京都",
                "EXTRACTED", null, "金色建筑与庭园", null, List.of(
                        new PhotoTagVO("architecture", "subject", BigDecimal.ONE),
                        new PhotoTagVO("gold", "dominant_color", BigDecimal.ONE)
                ), "COMPLETED", null, "deepseek", "stage5-v2", 1, takenAt, takenAt, takenAt
        );
    }

    private AppMessages messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return new AppMessages(source);
    }
}
