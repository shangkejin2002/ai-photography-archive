package com.example.usercenter.portfolio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveLocationNormalizerTest {

    @Test
    void matchesChineseJapaneseAndRomanizedKinkakujiNames() {
        assertThat(ArchiveLocationNormalizer.matches("金阁寺，京都", "Kinkaku-ji")).isTrue();
        assertThat(ArchiveLocationNormalizer.matches("金閣寺", "鹿苑寺")).isTrue();
        assertThat(ArchiveLocationNormalizer.matches("Rokuon-ji, Kyoto", "金阁寺")).isTrue();
        assertThat(ArchiveLocationNormalizer.matches("清水寺，京都", "Kinkaku-ji")).isFalse();
    }
}
