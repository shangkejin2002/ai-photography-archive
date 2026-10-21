package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.usercenter.mapper.PhotoTagMapper;
import com.example.usercenter.mapper.TagMapper;
import com.example.usercenter.i18n.AppMessages;
import com.example.usercenter.model.entity.Photo;
import com.example.usercenter.model.entity.PhotoTag;
import com.example.usercenter.model.entity.Tag;
import com.example.usercenter.model.vo.InsightItemVO;
import com.example.usercenter.model.vo.MonthlyCountVO;
import com.example.usercenter.model.vo.ShootingInsightsVO;
import com.example.usercenter.service.PhotoService;
import com.example.usercenter.service.ShootingInsightsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
public class ShootingInsightsServiceImpl implements ShootingInsightsService {

    private static final Set<String> SUBJECT_TYPES = Set.of("subject", "scene", "general");
    private static final Set<String> COLOR_TYPES = Set.of("dominant_color", "background_color", "color");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PhotoService photoService;
    private final PhotoTagMapper photoTagMapper;
    private final TagMapper tagMapper;
    private final AppMessages messages;

    public ShootingInsightsServiceImpl(PhotoService photoService, PhotoTagMapper photoTagMapper, TagMapper tagMapper, AppMessages messages) {
        this.photoService = photoService;
        this.photoTagMapper = photoTagMapper;
        this.tagMapper = tagMapper;
        this.messages = messages;
    }

    @Override
    public ShootingInsightsVO getOwnedInsights(Long userId) {
        List<Photo> photos = photoService.lambdaQuery()
                .eq(Photo::getUserId, userId)
                .orderByAsc(Photo::getTakenAt)
                .list();
        long total = photos.size();
        long favorites = photos.stream().filter(photo -> Integer.valueOf(1).equals(photo.getFavorite())).count();
        long exif = photos.stream().filter(this::hasExif).count();
        long ai = photos.stream().filter(photo -> "COMPLETED".equals(photo.getAnalysisStatus())).count();
        long gps = photos.stream().filter(photo -> photo.getGpsLatitude() != null && photo.getGpsLongitude() != null).count();
        long namedLocations = photos.stream().filter(photo -> hasText(photo.getLocationName())).count();

        Map<String, Long> cameras = countLabels(photos, this::cameraLabel);
        Map<String, Long> lenses = countLabels(photos, Photo::getLensModel);
        Map<String, Long> apertures = countLabels(photos, photo -> decimalLabel(photo.getAperture(), "f/"));
        Map<String, Long> isoValues = countLabels(photos, photo -> photo.getIsoValue() == null ? null : "ISO " + photo.getIsoValue());
        Map<String, Long> locations = countLabels(photos, Photo::getLocationName);
        Map<String, Long> focalRanges = focalRanges(photos);
        Set<Long> activePhotoIds = photos.stream().map(Photo::getId).collect(java.util.stream.Collectors.toSet());
        Map<String, Map<String, Long>> tagCounts = tagCountsByType(userId, activePhotoIds);
        Map<String, Long> subjects = countsForTypes(tagCounts, SUBJECT_TYPES);
        Map<String, Long> dominantColors = countsForTypes(tagCounts, COLOR_TYPES);
        Map<String, Long> moods = tagCounts.getOrDefault("mood", Map.of());
        Map<String, Long> lighting = tagCounts.getOrDefault("lighting", Map.of());

        List<InsightItemVO> rankedSubjects = ranked(subjects, totalCount(subjects), 5);
        List<InsightItemVO> rankedColors = ranked(dominantColors, totalCount(dominantColors), 4);
        List<InsightItemVO> rankedMoods = ranked(moods, totalCount(moods), 4);
        List<InsightItemVO> rankedLighting = ranked(lighting, totalCount(lighting), 3);
        List<String> observationDetails = observationDetails(
                photos, subjects, focalRanges, cameras, lenses, apertures, isoValues,
                locations, dominantColors, moods, lighting
        );

        return new ShootingInsightsVO(
                total, favorites, exif, ai, gps, namedLocations,
                percentage(exif, total), percentage(ai, total), percentage(gps, total),
                monthlyCounts(photos),
                ranked(cameras, total, 4), ranked(lenses, total, 4),
                ranked(apertures, apertures.values().stream().mapToLong(Long::longValue).sum(), 5),
                ranked(isoValues, isoValues.values().stream().mapToLong(Long::longValue).sum(), 5),
                orderedFocalItems(focalRanges), ranked(locations, total, 5),
                rankedSubjects, rankedColors, rankedMoods, rankedLighting,
                observationDetails.get(0), observationDetails
        );
    }

    private Map<String, Map<String, Long>> tagCountsByType(Long userId, Set<Long> activePhotoIds) {
        if (activePhotoIds.isEmpty()) return Map.of();
        List<PhotoTag> links = photoTagMapper.selectList(new LambdaQueryWrapper<PhotoTag>()
                .eq(PhotoTag::getUserId, userId)
                .in(PhotoTag::getPhotoId, activePhotoIds));
        if (links.isEmpty()) return Map.of();
        List<Long> tagIds = links.stream().map(PhotoTag::getTagId).distinct().toList();
        Map<Long, Tag> tags = new HashMap<>();
        tagMapper.selectBatchIds(tagIds).stream()
                .filter(tag -> userId.equals(tag.getUserId()))
                .forEach(tag -> tags.put(tag.getId(), tag));
        Map<String, Map<String, Long>> counts = new HashMap<>();
        for (PhotoTag link : links) {
            Tag tag = tags.get(link.getTagId());
            if (tag != null && hasText(tag.getName()) && hasText(tag.getTagType())) {
                counts.computeIfAbsent(tag.getTagType(), ignored -> new HashMap<>())
                        .merge(tag.getName().trim(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private Map<String, Long> countsForTypes(Map<String, Map<String, Long>> counts, Set<String> types) {
        Map<String, Long> result = new HashMap<>();
        for (String type : types) {
            counts.getOrDefault(type, Map.of()).forEach((label, count) -> result.merge(label, count, Long::sum));
        }
        return result;
    }

    private long totalCount(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private List<MonthlyCountVO> monthlyCounts(List<Photo> photos) {
        Map<YearMonth, Long> counts = new HashMap<>();
        photos.stream().map(Photo::getTakenAt).filter(value -> value != null)
                .map(YearMonth::from).forEach(month -> counts.merge(month, 1L, Long::sum));
        if (counts.isEmpty()) return List.of();
        YearMonth latest = counts.keySet().stream().max(Comparator.naturalOrder()).orElseThrow();
        List<MonthlyCountVO> result = new ArrayList<>();
        for (int offset = 11; offset >= 0; offset--) {
            YearMonth month = latest.minusMonths(offset);
            result.add(new MonthlyCountVO(month.format(MONTH_FORMAT), counts.getOrDefault(month, 0L)));
        }
        return List.copyOf(result);
    }

    private Map<String, Long> focalRanges(List<Photo> photos) {
        Map<String, Long> result = new LinkedHashMap<>();
        List.of("<24mm", "24–35mm", "36–50mm", "51–85mm", "86–135mm", "135mm+")
                .forEach(label -> result.put(label, 0L));
        for (Photo photo : photos) {
            Double focal = null;
            if (photo.getFocalLength35mm() != null) focal = photo.getFocalLength35mm().doubleValue();
            else if (photo.getFocalLength() != null) focal = photo.getFocalLength().doubleValue();
            if (focal == null || focal <= 0) continue;
            String label;
            if (focal < 24) label = "<24mm";
            else if (focal <= 35) label = "24–35mm";
            else if (focal <= 50) label = "36–50mm";
            else if (focal <= 85) label = "51–85mm";
            else if (focal <= 135) label = "86–135mm";
            else label = "135mm+";
            result.merge(label, 1L, Long::sum);
        }
        return result;
    }

    private List<InsightItemVO> orderedFocalItems(Map<String, Long> counts) {
        long denominator = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .map(entry -> new InsightItemVO(entry.getKey(), entry.getValue(), percentage(entry.getValue(), denominator)))
                .toList();
    }

    private Map<String, Long> countLabels(List<Photo> photos, Function<Photo, String> extractor) {
        Map<String, Long> result = new HashMap<>();
        for (Photo photo : photos) {
            String label = extractor.apply(photo);
            if (hasText(label)) result.merge(label.trim(), 1L, Long::sum);
        }
        return result;
    }

    private List<InsightItemVO> ranked(Map<String, Long> counts, long denominator, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> new InsightItemVO(entry.getKey(), entry.getValue(), percentage(entry.getValue(), denominator)))
                .toList();
    }

    private List<String> observationDetails(
            List<Photo> photos,
            Map<String, Long> subjects,
            Map<String, Long> focalRanges,
            Map<String, Long> cameras,
            Map<String, Long> lenses,
            Map<String, Long> apertures,
            Map<String, Long> isoValues,
            Map<String, Long> locations,
            Map<String, Long> colors,
            Map<String, Long> moods,
            Map<String, Long> lighting
    ) {
        if (photos.isEmpty()) return List.of(messages.get("insights.observation.empty"));
        List<String> details = new ArrayList<>();
        String subject = topLabel(subjects);
        String focal = topLabel(focalRanges.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll));
        String season = dominantSeason(photos);
        if (subject != null && focal != null) {
            details.add(messages.get("insights.observation.subjectFocal",
                    season == null ? messages.get("insights.season.varied") : season,
                    translateTag(subject), focal.replace("mm", " mm")));
        } else if (subject != null) {
            details.add(messages.get("insights.observation.subject", translateTag(subject)));
        } else if (focal != null) {
            details.add(messages.get("insights.observation.focal", focal.replace("mm", " mm")));
        }

        String color = topLabel(colors);
        String mood = topLabel(moods);
        String light = topLabel(lighting);
        if (color != null || mood != null || light != null) {
            List<String> traits = new ArrayList<>();
            if (color != null) traits.add(messages.get("insights.trait.color", translateTag(color)));
            if (light != null) traits.add(messages.get("insights.trait.light", translateTag(light)));
            if (mood != null) traits.add(messages.get("insights.trait.mood", translateTag(mood)));
            details.add(messages.get("insights.observation.visual", String.join(messages.get("insights.joiner"), traits)));
        }

        String camera = topLabel(cameras);
        String lens = topLabel(lenses);
        String aperture = topLabel(apertures);
        String iso = topLabel(isoValues);
        if (camera != null || lens != null) {
            String equipment = camera == null ? lens : lens == null ? camera : messages.get("insights.equipment.pair", camera, lens);
            String settings = aperture == null ? iso : iso == null ? aperture : messages.get("insights.equipment.settingsPair", aperture, iso);
            details.add(settings == null
                    ? messages.get("insights.observation.equipment", equipment)
                    : messages.get("insights.observation.equipmentSettings", equipment, settings));
        }

        String location = topLabel(locations);
        if (location != null) {
            details.add(messages.get("insights.observation.location", location));
        }

        if (details.isEmpty()) {
            details.add(messages.get("insights.observation.forming"));
        }
        return List.copyOf(details.subList(0, Math.min(4, details.size())));
    }

    private String dominantSeason(List<Photo> photos) {
        Map<String, Long> counts = new HashMap<>();
        for (Photo photo : photos) {
            LocalDateTime takenAt = photo.getTakenAt();
            if (takenAt == null) continue;
            int month = takenAt.getMonthValue();
            String season = month >= 3 && month <= 5 ? messages.get("season.spring") : month <= 8 && month >= 6 ? messages.get("season.summer")
                    : month >= 9 && month <= 11 ? messages.get("season.autumn") : messages.get("season.winter");
            counts.merge(season, 1L, Long::sum);
        }
        return topLabel(counts);
    }

    private String topLabel(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse(null);
    }

    private String translateTag(String value) {
        String key = switch (value.toLowerCase(Locale.ROOT)) {
            case "portrait", "person", "people" -> "people";
            case "temple", "shrine" -> "temple";
            case "object", "objects", "still life" -> "object";
            case "mountain", "mountains" -> "mountain";
            case "flower", "flowers", "floral" -> "flower";
            case "water", "lake", "river" -> "water";
            case "city", "urban" -> "city";
            case "golden" -> "gold";
            case "gray", "grey" -> "gray";
            case "quiet", "serene", "peaceful", "tranquil", "silent" -> "calm";
            case "soft light", "soft lighting" -> "softLight";
            case "diffused", "diffused light" -> "diffused";
            case "backlit", "backlight" -> "backlit";
            case "misty", "foggy" -> "misty";
            default -> value.toLowerCase(Locale.ROOT).replace(' ', '_');
        };
        return messages.getOrDefault("tag." + key, value);
    }

    private String cameraLabel(Photo photo) {
        String make = hasText(photo.getCameraMake()) ? photo.getCameraMake().trim() : null;
        String model = hasText(photo.getCameraModel()) ? photo.getCameraModel().trim() : null;
        if (make == null) return model;
        if (model == null || make.toLowerCase(Locale.ROOT).contains(model.toLowerCase(Locale.ROOT))) return make;
        return make + " " + model;
    }

    private String decimalLabel(BigDecimal value, String prefix) {
        return value == null ? null : prefix + value.stripTrailingZeros().toPlainString();
    }

    private boolean hasExif(Photo photo) {
        return "EXTRACTED".equals(photo.getMetadataStatus()) || photo.getCameraModel() != null
                || photo.getLensModel() != null || photo.getTakenAt() != null || photo.getIsoValue() != null;
    }

    private int percentage(long value, long denominator) {
        if (denominator <= 0) return 0;
        return (int) Math.round(value * 100.0 / denominator);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
