package com.example.usercenter.service.impl;

import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.i18n.AppMessages;
import com.example.usercenter.model.vo.AICreatePreviewVO;
import com.example.usercenter.model.vo.PhotoCandidateVO;
import com.example.usercenter.model.vo.PhotoTagVO;
import com.example.usercenter.model.vo.PhotoVO;
import com.example.usercenter.model.vo.PortfolioIntentVO;
import com.example.usercenter.portfolio.DeepSeekPortfolioIntentClient;
import com.example.usercenter.portfolio.ArchiveLocationNormalizer;
import com.example.usercenter.portfolio.PortfolioIntentResult;
import com.example.usercenter.portfolio.PortfolioTechnicalQueryParser;
import com.example.usercenter.service.AICreateService;
import com.example.usercenter.service.PhotoService;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AICreateServiceImpl implements AICreateService {

    private final DeepSeekPortfolioIntentClient intentClient;
    private final PhotoService photoService;
    private final AppMessages messages;

    public AICreateServiceImpl(DeepSeekPortfolioIntentClient intentClient, PhotoService photoService, AppMessages messages) {
        this.intentClient = intentClient;
        this.photoService = photoService;
        this.messages = messages;
    }

    @Override
    public AICreatePreviewVO preview(Long userId, String prompt) {
        List<PhotoVO> photos = photoService.listOwned(userId, null);
        List<String> availableTags = photos.stream()
                .flatMap(photo -> photo.aiTags().stream())
                .filter(tag -> Set.of("scene", "subject", "visual_element", "color", "dominant_color",
                        "background_color", "foreground_color", "lighting", "weather", "composition",
                        "season_visual", "mood", "activity", "depth", "general").contains(tag.type()))
                .map(PhotoTagVO::name)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .limit(120)
                .toList();
        PortfolioIntentResult aiParsed;
        try {
            aiParsed = intentClient.parse(prompt.trim(), availableTags);
        } catch (IllegalStateException e) {
            if (!PortfolioTechnicalQueryParser.hasDeterministicCriteria(prompt)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, e.getMessage());
            }
            aiParsed = PortfolioTechnicalQueryParser.fallback(prompt);
        }
        PortfolioIntentResult parsed = PortfolioTechnicalQueryParser.enrich(prompt, aiParsed);
        List<PhotoCandidateVO> ranked = photos.stream()
                .map(photo -> score(photo, parsed))
                .filter(candidate -> candidate != null)
                .sorted(candidateComparator(parsed.sort()))
                .toList();
        List<PhotoCandidateVO> candidates = Boolean.TRUE.equals(parsed.diversityRequested())
                ? selectDiverse(ranked, parsed.limit())
                : ranked.stream().limit(parsed.limit()).toList();
        PortfolioIntentVO intent = new PortfolioIntentVO(
                parsed.year(), parsed.season(), parsed.location(), parsed.iso(), parsed.favoriteOnly(),
                parsed.cameraModel(), parsed.lensModel(), parsed.subjects(), parsed.colors(),
                parsed.visualTags(), parsed.excludeTags(), parsed.diversityRequested(), parsed.orientation(),
                parsed.shutterSpeed(), parsed.aperture(), parsed.focalLength(), parsed.hasPeople(),
                parsed.peopleCount(), parsed.backgroundColors(), parsed.moods(), parsed.requiredTags(),
                parsed.preferredTags(),
                parsed.limit(), parsed.sort()
        );
        List<String> diagnostics = candidates.isEmpty() ? diagnoseNoMatches(photos, parsed) : List.of();
        List<String> suggestions = candidates.isEmpty() ? relaxationSuggestions(parsed) : List.of();
        return new AICreatePreviewVO(
                intent, parsed.suggestedTitle(), parsed.suggestedSubtitle(), candidates, diagnostics, suggestions
        );
    }

    private PhotoCandidateVO score(PhotoVO photo, PortfolioIntentResult intent) {
        List<String> reasons = new ArrayList<>();
        int score = 1;

        if (intent.year() != null) {
            if (photo.takenAt() == null || photo.takenAt().getYear() != intent.year()) return null;
            score += 25;
            reasons.add(messages.get("ai.reason.year", intent.year()));
        }
        if (intent.season() != null) {
            boolean dateMatch = photo.takenAt() != null && season(photo.takenAt().getMonth()).equals(intent.season());
            boolean visualMatch = hasTag(photo, "season_visual", intent.season());
            if (!dateMatch && !visualMatch) return null;
            score += 20;
            reasons.add(seasonLabel(intent.season()));
        }
        if (intent.iso() != null) {
            if (!intent.iso().equals(photo.isoValue())) return null;
            score += 20;
            reasons.add("ISO " + intent.iso());
        }
        if (intent.shutterSpeed() != null) {
            BigDecimal expected = PortfolioTechnicalQueryParser.exposureSeconds(intent.shutterSpeed());
            BigDecimal actual = PortfolioTechnicalQueryParser.exposureSeconds(photo.shutterSpeed());
            if (expected == null || actual == null || expected.compareTo(actual) != 0) return null;
            score += 25;
            reasons.add(intent.shutterSpeed());
        }
        if (intent.aperture() != null) {
            if (!approximately(photo.aperture(), intent.aperture(), new BigDecimal("0.05"))) return null;
            score += 22;
            reasons.add("f/" + intent.aperture().stripTrailingZeros().toPlainString());
        }
        if (intent.focalLength() != null) {
            BigDecimal actual = photo.focalLength() != null ? photo.focalLength()
                    : photo.focalLength35mm() == null ? null : BigDecimal.valueOf(photo.focalLength35mm());
            if (!approximately(actual, intent.focalLength(), new BigDecimal("0.5"))) return null;
            score += 22;
            reasons.add(intent.focalLength().stripTrailingZeros().toPlainString() + "mm");
        }
        if (intent.hasPeople() != null) {
            boolean present = hasPeople(photo);
            if (present != intent.hasPeople()) return null;
            score += 20;
            reasons.add(messages.get(present ? "ai.reason.people" : "ai.reason.noPeople"));
        }
        if (intent.peopleCount() != null) {
            Integer actual = peopleCount(photo);
            if (actual == null || !actual.equals(intent.peopleCount())) return null;
            score += 18;
            reasons.add(messages.get("ai.reason.peopleCount", actual));
        }
        if (Boolean.TRUE.equals(intent.favoriteOnly())) {
            if (!Boolean.TRUE.equals(photo.favorite())) return null;
            score += 8;
            reasons.add(messages.get("ai.reason.favorite"));
        }
        if (intent.cameraModel() != null) {
            if (!contains(photo.cameraModel(), intent.cameraModel())) return null;
            score += 15;
            reasons.add(photo.cameraModel());
        }
        if (intent.lensModel() != null) {
            if (!contains(photo.lensModel(), intent.lensModel())) return null;
            score += 15;
            reasons.add(photo.lensModel());
        }
        if (intent.location() != null) {
            boolean locationMatch = ArchiveLocationNormalizer.matches(photo.locationName(), intent.location())
                    || contains(photo.aiDescription(), intent.location())
                    || anyTagContains(photo, intent.location());
            if (!locationMatch) return null;
            score += ArchiveLocationNormalizer.matches(photo.locationName(), intent.location()) ? 40 : 18;
            reasons.add(photo.locationName() != null ? photo.locationName() : intent.location());
        }
        if (intent.orientation() != null) {
            if (!matchesOrientation(photo, intent.orientation())) return null;
            score += 8;
            reasons.add(switch (intent.orientation()) {
                case "portrait" -> messages.get("ai.reason.portrait");
                case "square" -> messages.get("ai.reason.square");
                default -> messages.get("ai.reason.landscape");
            });
        }
        for (String excluded : intent.excludeTags()) {
            if (semanticMatch(photo, excluded)) return null;
        }
        for (String required : intent.requiredTags()) {
            if (!semanticMatch(photo, required)) return null;
            score += 14;
            reasons.add(required);
        }

        int softMatches = 0;
        for (String subject : intent.subjects()) {
            if (semanticMatch(photo, subject)) {
                score += 10;
                softMatches++;
                reasons.add(subject);
            }
        }
        for (String color : intent.colors()) {
            if (hasTag(photo, "color", color) || semanticMatch(photo, color)) {
                score += 8;
                softMatches++;
                reasons.add(color);
            }
        }
        for (String tag : intent.visualTags()) {
            if (semanticMatch(photo, tag)) {
                score += 7;
                softMatches++;
                reasons.add(tag);
            }
        }
        for (String color : intent.backgroundColors()) {
            if (hasTag(photo, "background_color", color)) {
                score += 16;
                softMatches++;
                reasons.add(messages.get("ai.reason.background", color));
            } else if (hasTag(photo, "color", color) || hasTag(photo, "dominant_color", color)) {
                score += 6;
                softMatches++;
                reasons.add(color);
            }
        }
        for (String mood : intent.moods()) {
            if (hasTag(photo, "mood", mood) || semanticMatch(photo, mood)) {
                score += 12;
                softMatches++;
                reasons.add(mood);
            }
        }
        for (String preferred : intent.preferredTags()) {
            if (semanticMatch(photo, preferred)) {
                score += 6;
                softMatches++;
                reasons.add(preferred);
            }
        }
        boolean hasOnlySoftCriteria = intent.year() == null && intent.season() == null && intent.location() == null
                && intent.iso() == null && !Boolean.TRUE.equals(intent.favoriteOnly())
                && intent.cameraModel() == null && intent.lensModel() == null && intent.orientation() == null
                && intent.shutterSpeed() == null && intent.aperture() == null && intent.focalLength() == null
                && intent.hasPeople() == null && intent.peopleCount() == null && intent.requiredTags().isEmpty()
                && (!intent.subjects().isEmpty() || !intent.colors().isEmpty() || !intent.visualTags().isEmpty()
                || !intent.backgroundColors().isEmpty() || !intent.moods().isEmpty() || !intent.preferredTags().isEmpty());
        if (hasOnlySoftCriteria && softMatches == 0) return null;
        return new PhotoCandidateVO(photo, Math.min(score, 100), reasons.stream().distinct().limit(6).toList());
    }

    private List<String> diagnoseNoMatches(List<PhotoVO> photos, PortfolioIntentResult intent) {
        if (photos.isEmpty()) return List.of(messages.get("ai.diagnostic.emptyLibrary"));
        List<PhotoVO> remaining = new ArrayList<>(photos);
        List<String> diagnostics = new ArrayList<>();

        remaining = diagnosticFilter(remaining, intent.year() == null ? null
                : photo -> photo.takenAt() != null && photo.takenAt().getYear() == intent.year(),
                intent.year() == null ? null : messages.get("ai.reason.year", intent.year()), diagnostics);
        remaining = diagnosticFilter(remaining, intent.season() == null ? null
                : photo -> (photo.takenAt() != null && season(photo.takenAt().getMonth()).equals(intent.season()))
                || hasTag(photo, "season_visual", intent.season()),
                intent.season() == null ? null : seasonLabel(intent.season()), diagnostics);
        remaining = diagnosticFilter(remaining, intent.location() == null ? null
                : photo -> ArchiveLocationNormalizer.matches(photo.locationName(), intent.location())
                || contains(photo.aiDescription(), intent.location()) || anyTagContains(photo, intent.location()),
                intent.location() == null ? null : messages.get("ai.condition.location", intent.location()), diagnostics);
        remaining = diagnosticFilter(remaining, intent.iso() == null ? null
                : photo -> intent.iso().equals(photo.isoValue()),
                intent.iso() == null ? null : "ISO " + intent.iso(), diagnostics);
        remaining = diagnosticFilter(remaining, intent.shutterSpeed() == null ? null
                : photo -> {
                    BigDecimal expected = PortfolioTechnicalQueryParser.exposureSeconds(intent.shutterSpeed());
                    BigDecimal actual = PortfolioTechnicalQueryParser.exposureSeconds(photo.shutterSpeed());
                    return expected != null && actual != null && expected.compareTo(actual) == 0;
                }, intent.shutterSpeed() == null ? null : messages.get("ai.condition.shutter", intent.shutterSpeed()), diagnostics);
        remaining = diagnosticFilter(remaining, intent.aperture() == null ? null
                : photo -> approximately(photo.aperture(), intent.aperture(), new BigDecimal("0.05")),
                intent.aperture() == null ? null : messages.get("ai.condition.aperture", intent.aperture()), diagnostics);
        remaining = diagnosticFilter(remaining, intent.focalLength() == null ? null
                : photo -> approximately(photo.focalLength() != null ? photo.focalLength()
                        : photo.focalLength35mm() == null ? null : BigDecimal.valueOf(photo.focalLength35mm()),
                        intent.focalLength(), new BigDecimal("0.5")),
                intent.focalLength() == null ? null : messages.get("ai.condition.focal", intent.focalLength()), diagnostics);
        remaining = diagnosticFilter(remaining, intent.hasPeople() == null ? null
                : photo -> hasPeople(photo) == intent.hasPeople(),
                intent.hasPeople() == null ? null : messages.get(Boolean.TRUE.equals(intent.hasPeople()) ? "ai.reason.people" : "ai.reason.noPeople"), diagnostics);
        for (String required : intent.requiredTags()) {
            remaining = diagnosticFilter(remaining, photo -> semanticMatch(photo, required),
                    messages.get("ai.condition.required", required), diagnostics);
        }

        if (remaining.isEmpty()) return List.copyOf(diagnostics);
        return List.of(messages.get("ai.diagnostic.semantic"));
    }

    private List<PhotoVO> diagnosticFilter(
            List<PhotoVO> photos,
            java.util.function.Predicate<PhotoVO> predicate,
            String label,
            List<String> diagnostics
    ) {
        if (predicate == null || photos.isEmpty()) return photos;
        List<PhotoVO> filtered = photos.stream().filter(predicate).toList();
        if (filtered.isEmpty()) {
            diagnostics.add(messages.get("ai.diagnostic.failedAt", photos.size(), label));
        }
        return filtered;
    }

    private List<String> relaxationSuggestions(PortfolioIntentResult intent) {
        List<String> suggestions = new ArrayList<>();
        if (intent.year() != null) suggestions.add(messages.get("ai.suggestion.year"));
        if (intent.season() != null) suggestions.add(messages.get("ai.suggestion.season"));
        if (intent.location() != null) suggestions.add(messages.get("ai.suggestion.location"));
        if (intent.iso() != null || intent.shutterSpeed() != null || intent.aperture() != null
                || intent.focalLength() != null) suggestions.add(messages.get("ai.suggestion.settings"));
        if (!intent.requiredTags().isEmpty()) suggestions.add(messages.get("ai.suggestion.required"));
        if (suggestions.isEmpty()) suggestions.add(messages.get("ai.suggestion.visual"));
        return suggestions.stream().distinct().limit(3).toList();
    }

    private List<PhotoCandidateVO> selectDiverse(List<PhotoCandidateVO> ranked, int limit) {
        if (ranked.size() <= 1) return ranked.stream().limit(limit).toList();
        List<PhotoCandidateVO> remaining = new ArrayList<>(ranked);
        List<PhotoCandidateVO> selected = new ArrayList<>();
        selected.add(remaining.remove(0));
        while (!remaining.isEmpty() && selected.size() < limit) {
            PhotoCandidateVO next = remaining.stream()
                    .max(Comparator.comparingDouble(candidate -> diversityValue(candidate, selected)))
                    .orElseThrow();
            selected.add(next);
            remaining.remove(next);
        }
        return List.copyOf(selected);
    }

    private double diversityValue(PhotoCandidateVO candidate, List<PhotoCandidateVO> selected) {
        Set<String> candidateFingerprint = fingerprint(candidate.photo());
        double greatestSimilarity = selected.stream()
                .map(PhotoCandidateVO::photo)
                .map(this::fingerprint)
                .mapToDouble(existing -> jaccard(candidateFingerprint, existing))
                .max().orElse(0);
        return candidate.score() - greatestSimilarity * 35;
    }

    private Set<String> fingerprint(PhotoVO photo) {
        Set<String> values = new LinkedHashSet<>();
        photo.aiTags().stream()
                .filter(tag -> Set.of("scene", "subject", "visual_element", "general").contains(tag.type()))
                .map(PhotoTagVO::name)
                .filter(name -> name != null && !name.isBlank())
                .map(this::normalize)
                .forEach(values::add);
        if (photo.locationName() != null && !photo.locationName().isBlank()) {
            values.add("location:" + normalize(photo.locationName()));
        }
        return values;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    private Comparator<PhotoCandidateVO> candidateComparator(String sort) {
        if ("oldest".equals(sort)) {
            return Comparator.comparing(candidate -> candidate.photo().takenAt() == null
                    ? candidate.photo().createTime() : candidate.photo().takenAt());
        }
        if ("newest".equals(sort)) {
            return Comparator.comparing((PhotoCandidateVO candidate) -> candidate.photo().takenAt() == null
                    ? candidate.photo().createTime() : candidate.photo().takenAt()).reversed();
        }
        return Comparator.comparing(PhotoCandidateVO::score).reversed()
                .thenComparing(candidate -> candidate.photo().createTime(), Comparator.reverseOrder());
    }

    private boolean hasTag(PhotoVO photo, String type, String expected) {
        return photo.aiTags().stream().anyMatch(tag -> type.equals(tag.type()) && equalsLoose(tag.name(), expected));
    }

    private boolean hasPeople(PhotoVO photo) {
        boolean explicitlyPresent = hasTag(photo, "people_presence", "present");
        if (explicitlyPresent) return true;
        return photo.aiTags().stream()
                .filter(tag -> Set.of("subject", "general").contains(tag.type()))
                .map(PhotoTagVO::name)
                .anyMatch(name -> concepts(name).stream().anyMatch(Set.of("person", "people", "man", "woman", "child")::contains));
    }

    private Integer peopleCount(PhotoVO photo) {
        return photo.aiTags().stream()
                .filter(tag -> "people_count".equals(tag.type()))
                .map(PhotoTagVO::name)
                .map(value -> {
                    try { return Integer.valueOf(value); } catch (NumberFormatException ignored) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    private boolean approximately(BigDecimal actual, BigDecimal expected, BigDecimal tolerance) {
        return actual != null && expected != null && actual.subtract(expected).abs().compareTo(tolerance) <= 0;
    }

    private boolean anyTagContains(PhotoVO photo, String expected) {
        return photo.aiTags().stream().map(PhotoTagVO::name).anyMatch(name -> contains(name, expected));
    }

    private boolean semanticMatch(PhotoVO photo, String expected) {
        if (expected == null || expected.isBlank()) return false;
        if (anyTagContains(photo, expected) || contains(photo.aiDescription(), expected)) return true;
        Set<String> expectedConcepts = concepts(expected);
        return photo.aiTags().stream()
                .map(PhotoTagVO::name)
                .filter(name -> name != null)
                .map(this::concepts)
                .anyMatch(actual -> actual.stream().anyMatch(expectedConcepts::contains));
    }

    private Set<String> concepts(String value) {
        String normalized = normalize(value);
        Set<String> result = new LinkedHashSet<>();
        result.add(normalized);
        addConceptGroup(result, normalized, Set.of("landscape", "scenery", "scenic", "view", "景色", "风景"),
                Set.of("landscape", "nature", "outdoor", "garden", "forest", "mountain", "water", "sky",
                        "architecture", "building", "temple", "shrine", "pavilion", "cityscape", "waterscape",
                        "park", "flower", "flowers", "wisteria"));
        addConceptGroup(result, normalized, Set.of("nature", "natural", "自然"),
                Set.of("nature", "landscape", "outdoor", "garden", "forest", "mountain", "water", "sky",
                        "park", "flower", "flowers", "plant", "tree", "wisteria"));
        addConceptGroup(result, normalized, Set.of("architecture", "building", "建筑"),
                Set.of("architecture", "building", "temple", "shrine", "pavilion", "cityscape", "interior"));
        addConceptGroup(result, normalized, Set.of("portrait", "people", "person", "人物", "人像"),
                Set.of("portrait", "people", "person", "man", "woman", "child"));
        addConceptGroup(result, normalized, Set.of("animal", "wildlife", "动物"),
                Set.of("animal", "wildlife", "bird", "cat", "dog"));
        addConceptGroup(result, normalized, Set.of("calm", "quiet", "serene", "peaceful", "tranquil", "安静", "宁静", "静谧"),
                Set.of("calm", "quiet", "serene", "peaceful", "tranquil", "misty", "soft light", "diffused", "reflection"));
        return result;
    }

    private void addConceptGroup(Set<String> result, String normalized, Set<String> triggers, Set<String> concepts) {
        if (triggers.stream().anyMatch(trigger -> normalized.equals(trigger) || normalized.contains(trigger))) {
            result.addAll(concepts);
        }
    }

    private boolean matchesOrientation(PhotoVO photo, String expected) {
        if (photo.width() == null || photo.height() == null || photo.width() <= 0 || photo.height() <= 0) return false;
        double ratio = (double) photo.width() / photo.height();
        return switch (expected) {
            case "portrait" -> ratio < .9;
            case "square" -> ratio >= .9 && ratio <= 1.1;
            default -> ratio > 1.1;
        };
    }

    private boolean contains(String value, String expected) {
        if (value == null || expected == null) return false;
        String left = normalize(value);
        String right = normalize(expected);
        return left.contains(right) || right.contains(left);
    }

    private boolean equalsLoose(String value, String expected) {
        return normalize(value).equals(normalize(expected)) || contains(value, expected);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", " ");
    }

    private String season(Month month) {
        int value = month.getValue();
        if (value >= 3 && value <= 5) return "spring";
        if (value >= 6 && value <= 8) return "summer";
        if (value >= 9 && value <= 11) return "autumn";
        return "winter";
    }

    private String seasonLabel(String value) {
        return messages.get("season." + value);
    }
}
