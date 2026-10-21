package com.example.usercenter.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AppMessages {
    private final MessageSource messageSource;

    public AppMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String key, Object... args) {
        return get(LocaleContextHolder.getLocale(), key, args);
    }

    public String get(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, key, supported(locale));
    }

    public String getOrDefault(String key, String fallback, Object... args) {
        return messageSource.getMessage(key, args, fallback, supported(LocaleContextHolder.getLocale()));
    }

    public boolean isJapanese() {
        return "ja".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());
    }

    private Locale supported(Locale locale) {
        return locale != null && "ja".equalsIgnoreCase(locale.getLanguage()) ? Locale.JAPAN : Locale.SIMPLIFIED_CHINESE;
    }
}
