package com.example.usercenter.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekPhotoDescriptionTranslatorTest {

    @Test
    void parsesOnlyValidNonBlankTranslations() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DeepSeekPhotoDescriptionTranslator translator = new DeepSeekPhotoDescriptionTranslator(
                objectMapper, "test-key", "https://api.deepseek.com/", "deepseek-flash", 60
        );

        var translations = translator.parseTranslations(objectMapper.readTree("""
                {"translations":[
                  {"id":12,"descriptionJa":"  雪の中の伝統建築です。  "},
                  {"id":0,"descriptionJa":"無効"},
                  {"id":13,"descriptionJa":""}
                ]}
                """));

        assertThat(translations).containsOnlyKeys(12L);
        assertThat(translations.get(12L)).isEqualTo("雪の中の伝統建築です。");
    }
}
