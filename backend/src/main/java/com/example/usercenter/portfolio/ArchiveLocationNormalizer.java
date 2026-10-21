package com.example.usercenter.portfolio;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Matches user-facing place names across Chinese, Japanese and romanized aliases. */
public final class ArchiveLocationNormalizer {

    private static final Map<String, Set<String>> ALIASES = aliases();

    private ArchiveLocationNormalizer() {
    }

    public static boolean matches(String storedLocation, String requestedLocation) {
        if (storedLocation == null || requestedLocation == null) return false;
        String stored = canonical(storedLocation);
        String requested = canonical(requestedLocation);
        return !stored.isBlank() && !requested.isBlank()
                && (stored.equals(requested) || stored.contains(requested) || requested.contains(stored));
    }

    public static String canonical(String value) {
        if (value == null) return "";
        String normalized = normalize(value);
        for (Map.Entry<String, Set<String>> entry : ALIASES.entrySet()) {
            if (entry.getValue().stream().anyMatch(alias -> normalized.equals(alias) || normalized.contains(alias))) {
                return entry.getKey();
            }
        }
        return normalized;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{Z}\\s_-]+", "")
                .trim();
    }

    private static Map<String, Set<String>> aliases() {
        Map<String, Set<String>> aliases = new LinkedHashMap<>();
        aliases.put("kinkakuji", Set.of("金阁寺", "金閣寺", "kinkakuji", "鹿苑寺", "rokuonji"));
        aliases.put("kiyomizudera", Set.of("清水寺", "kiyomizudera", "kiyomizutemple"));
        aliases.put("fushimiinari", Set.of("伏见稻荷", "伏見稲荷", "伏见稻荷大社", "伏見稲荷大社", "fushimiinari", "fushimiinaritaisha"));
        aliases.put("arashiyama", Set.of("岚山", "嵐山", "arashiyama"));
        return Map.copyOf(aliases);
    }
}
