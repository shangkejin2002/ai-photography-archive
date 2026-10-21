package com.example.usercenter.portfolio;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts deterministic camera settings before semantic AI matching. */
public final class PortfolioTechnicalQueryParser {

    private static final Pattern SHUTTER = Pattern.compile("(?i)(?:(?:快门|シャッター)(?:速度)?[为是：:\\s]*)?(\\d+)\\s*/\\s*(\\d+)\\s*(?:s|sec(?:ond)?s?|秒)");
    private static final Pattern APERTURE = Pattern.compile("(?i)(?:(?:光圈|絞り)[为是：:\\s]*)?f\\s*/?\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern FOCAL_LENGTH = Pattern.compile("(?i)(?:(?:焦距|焦点距離)[为是：:\\s]*)?(\\d+(?:\\.\\d+)?)\\s*mm");
    private static final Pattern ISO = Pattern.compile("(?i)iso\\s*[-:]?\\s*(\\d+)");
    private static final Pattern ARABIC_LIMIT = Pattern.compile("(?:(?:最多|选择|找出|找|要|最大|選んで|探して)?\\s*)(\\d{1,2})\\s*(?:张|枚)");
    private static final Pattern CHINESE_LIMIT = Pattern.compile("([一二两三四五六七八九十]{1,3})张");

    private PortfolioTechnicalQueryParser() {
    }

    public static PortfolioIntentResult enrich(String prompt, PortfolioIntentResult source) {
        String text = prompt == null ? "" : prompt.trim();
        String shutter = firstShutter(text, source.shutterSpeed());
        BigDecimal aperture = firstDecimal(APERTURE, text, source.aperture());
        BigDecimal focalLength = firstDecimal(FOCAL_LENGTH, text, source.focalLength());
        Integer iso = firstInteger(ISO, text, source.iso());
        int limit = firstLimit(text, source.limit());
        List<String> backgroundColors = detectedBackgroundColors(normalized(text), source.backgroundColors());
        List<String> moods = detectedMoods(normalized(text), source.moods());
        List<String> requiredTags = new ArrayList<>(source.requiredTags() == null ? List.of() : source.requiredTags());
        List<String> preferredTags = new ArrayList<>(source.preferredTags() == null ? List.of() : source.preferredTags());
        if (!hasExplicitMandatoryLanguage(normalized(text))) {
            preferredTags.addAll(requiredTags);
            requiredTags.clear();
        }
        Boolean hasPeople = source.hasPeople();
        String normalized = text.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "不要人物", "不要人", "无人", "没有人", "人物なし", "人なし", "人物を除く", "without people", "no people")) {
            hasPeople = false;
        } else if (containsAny(normalized, "有人", "人物", "人像", "人物あり", "ポートレート", "people", "person", "portrait")) {
            hasPeople = true;
        }
        return new PortfolioIntentResult(
                source.year(), source.season(), source.location(), iso, source.favoriteOnly(),
                source.cameraModel(), source.lensModel(), source.subjects(), source.colors(), source.visualTags(),
                source.excludeTags(), source.diversityRequested(), source.orientation(), shutter, aperture,
                focalLength, hasPeople, source.peopleCount(), backgroundColors, moods,
                distinct(requiredTags), distinct(preferredTags), limit, source.sort(),
                source.suggestedTitle(), source.suggestedSubtitle()
        );
    }

    public static boolean hasDeterministicCriteria(String prompt) {
        if (prompt == null) return false;
        String normalized = prompt.toLowerCase(Locale.ROOT);
        return SHUTTER.matcher(prompt).find() || APERTURE.matcher(prompt).find()
                || FOCAL_LENGTH.matcher(prompt).find() || ISO.matcher(prompt).find()
                || containsAny(normalized, "有人", "人物", "人像", "不要人物", "不要人", "无人", "没有人", "人物あり", "人物なし", "人なし", "ポートレート",
                "people", "person", "portrait", "without people", "no people")
                || !detectedBackgroundColors(normalized, List.of()).isEmpty()
                || !detectedMoods(normalized, List.of()).isEmpty();
    }

    public static PortfolioIntentResult fallback(String prompt) {
        PortfolioIntentResult empty = new PortfolioIntentResult(
                null, null, null, null, false, null, null,
                List.of(), List.of(), List.of(), List.of(), false, null,
                null, null, null, null, null, List.of(), List.of(), List.of(), List.of(),
                20, "relevance", isJapaneseText(prompt) ? "写真 Portfolio" : "摄影作品集", prompt == null ? "" : prompt.trim()
        );
        return enrich(prompt, empty);
    }

    public static BigDecimal exposureSeconds(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher fraction = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(value);
        if (fraction.find()) {
            BigDecimal numerator = new BigDecimal(fraction.group(1));
            BigDecimal denominator = new BigDecimal(fraction.group(2));
            if (denominator.signum() == 0) return null;
            return numerator.divide(denominator, 10, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        }
        Matcher decimal = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(value);
        return decimal.find() ? new BigDecimal(decimal.group(1)).stripTrailingZeros() : null;
    }

    private static String firstShutter(String prompt, String fallback) {
        Matcher matcher = SHUTTER.matcher(prompt);
        if (!matcher.find()) return fallback;
        return matcher.group(1) + "/" + matcher.group(2) + "s";
    }

    private static BigDecimal firstDecimal(Pattern pattern, String prompt, BigDecimal fallback) {
        Matcher matcher = pattern.matcher(prompt);
        return matcher.find() ? new BigDecimal(matcher.group(1)).stripTrailingZeros() : fallback;
    }

    private static Integer firstInteger(Pattern pattern, String prompt, Integer fallback) {
        Matcher matcher = pattern.matcher(prompt);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : fallback;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean hasExplicitMandatoryLanguage(String text) {
        return containsAny(text, "必须", "一定要", "务必", "必须包含", "必須", "必ず", "must", "required", "require");
    }

    private static List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private static List<String> detectedBackgroundColors(String text, List<String> existing) {
        List<String> result = new ArrayList<>(existing == null ? List.of() : existing);
        if (!containsAny(text, "背景", "background")) return List.copyOf(result);
        addWhenPresent(result, text, "green", "绿色", "绿意", "緑", "green");
        addWhenPresent(result, text, "blue", "蓝色", "青", "ブルー", "blue");
        addWhenPresent(result, text, "gold", "金色", "gold", "golden");
        addWhenPresent(result, text, "red", "红色", "赤", "red");
        addWhenPresent(result, text, "white", "白色", "白", "white");
        addWhenPresent(result, text, "black", "黑色", "黒", "black");
        addWhenPresent(result, text, "gray", "灰色", "灰", "グレー", "gray", "grey");
        return List.copyOf(result);
    }

    private static List<String> detectedMoods(String text, List<String> existing) {
        List<String> result = new ArrayList<>(existing == null ? List.of() : existing);
        addWhenPresent(result, text, "calm", "安静", "宁静", "静谧", "静か", "穏やか", "quiet", "calm", "serene", "tranquil");
        addWhenPresent(result, text, "dramatic", "戏剧", "ドラマチック", "dramatic");
        addWhenPresent(result, text, "warm", "温暖", "暖か", "warm");
        addWhenPresent(result, text, "melancholic", "忧郁", "憂鬱", "melancholic");
        return List.copyOf(result);
    }

    private static void addWhenPresent(List<String> target, String text, String canonical, String... variants) {
        if (containsAny(text, variants) && !target.contains(canonical)) target.add(canonical);
    }

    private static int firstLimit(String prompt, Integer fallback) {
        Matcher arabic = ARABIC_LIMIT.matcher(prompt);
        if (arabic.find()) return clampLimit(Integer.parseInt(arabic.group(1)));
        Matcher chinese = CHINESE_LIMIT.matcher(prompt);
        if (chinese.find()) return clampLimit(chineseNumber(chinese.group(1)));
        return clampLimit(fallback == null ? 20 : fallback);
    }

    private static int chineseNumber(String value) {
        if ("十".equals(value)) return 10;
        int ten = value.indexOf('十');
        if (ten >= 0) {
            int tens = ten == 0 ? 1 : chineseDigit(value.charAt(0));
            int ones = ten == value.length() - 1 ? 0 : chineseDigit(value.charAt(value.length() - 1));
            return tens * 10 + ones;
        }
        return chineseDigit(value.charAt(0));
    }

    private static int chineseDigit(char value) {
        return switch (value) {
            case '一' -> 1; case '二', '两' -> 2; case '三' -> 3; case '四' -> 4;
            case '五' -> 5; case '六' -> 6; case '七' -> 7; case '八' -> 8; case '九' -> 9;
            default -> 0;
        };
    }

    private static int clampLimit(int value) {
        return Math.max(1, Math.min(20, value));
    }

    private static boolean isJapaneseText(String value) {
        return value != null && value.matches(".*[ぁ-んァ-ヶ].*");
    }
}
