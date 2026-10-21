package com.example.usercenter.analysis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.usercenter.mapper.PhotoMapper;
import com.example.usercenter.mapper.PhotoTagMapper;
import com.example.usercenter.mapper.TagMapper;
import com.example.usercenter.model.entity.Photo;
import com.example.usercenter.model.entity.PhotoTag;
import com.example.usercenter.model.entity.Tag;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PhotoAnalysisPersistenceService {

    public static final String ANALYSIS_VERSION = "stage5-v2";
    public static final Set<String> AI_TAG_TYPES = Set.of(
            "scene", "subject", "visual_element", "color", "lighting", "weather",
            "composition", "season_visual", "general", "dominant_color", "background_color",
            "foreground_color", "mood", "activity", "depth", "people_presence", "people_count",
            "people_prominence"
    );

    private final PhotoMapper photoMapper;
    private final TagMapper tagMapper;
    private final PhotoTagMapper photoTagMapper;

    public PhotoAnalysisPersistenceService(PhotoMapper photoMapper, TagMapper tagMapper, PhotoTagMapper photoTagMapper) {
        this.photoMapper = photoMapper;
        this.tagMapper = tagMapper;
        this.photoTagMapper = photoTagMapper;
    }

    @Transactional
    public void markAnalyzing(Photo photo) {
        photo.setAnalysisStatus("ANALYZING");
        photo.setAnalysisError(null);
        photo.setAnalysisAttempts((photo.getAnalysisAttempts() == null ? 0 : photo.getAnalysisAttempts()) + 1);
        photo.setUpdateTime(LocalDateTime.now());
        photoMapper.updateById(photo);
    }

    @Transactional
    public void complete(Photo photo, PhotoAnalysisResult result, String model) {
        deleteAiLinks(photo.getId());
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("scene", result.scenes());
        groups.put("subject", result.subjects());
        groups.put("visual_element", result.visualElements());
        groups.put("color", result.colors());
        groups.put("lighting", result.lighting());
        groups.put("weather", result.weather());
        groups.put("composition", result.compositions());
        groups.put("season_visual", result.visualSeasons());
        groups.put("dominant_color", result.dominantColors());
        groups.put("background_color", result.backgroundColors());
        groups.put("foreground_color", result.foregroundColors());
        groups.put("mood", result.moods());
        groups.put("activity", result.activities());
        groups.put("depth", result.depthCues());
        groups.put("general", result.tags());
        groups.forEach((type, names) -> names.forEach(name -> linkTag(photo, type, name)));
        if (Boolean.TRUE.equals(result.peoplePresent())) {
            linkTag(photo, "people_presence", "present", new BigDecimal("0.95"));
        }
        if (result.peopleCount() != null) {
            linkTag(photo, "people_count", result.peopleCount().toString(), new BigDecimal("0.85"));
        }
        if (result.peopleProminence() != null && !result.peopleProminence().isBlank()) {
            linkTag(photo, "people_prominence", result.peopleProminence(), new BigDecimal("0.80"));
        }

        photo.setAiDescription(result.description());
        photo.setAiDescriptionJa(result.descriptionJa());
        photo.setAiDescriptionJaVersion(result.descriptionJa().isBlank() ? null : "native-v1");
        photo.setAnalysisStatus("COMPLETED");
        photo.setAnalysisError(null);
        photo.setAnalysisModel(model);
        photo.setAnalysisVersion(ANALYSIS_VERSION);
        photo.setAnalyzedAt(LocalDateTime.now());
        photo.setUpdateTime(LocalDateTime.now());
        photoMapper.updateById(photo);
    }

    @Transactional
    public void fail(Photo photo, String error) {
        photo.setAnalysisStatus("FAILED");
        photo.setAnalysisError(error == null ? "AI 分析失败" : error.substring(0, Math.min(error.length(), 1000)));
        photo.setUpdateTime(LocalDateTime.now());
        photoMapper.updateById(photo);
    }

    private void deleteAiLinks(Long photoId) {
        List<Long> tagIds = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                        .in(Tag::getTagType, AI_TAG_TYPES))
                .stream().map(Tag::getId).toList();
        if (!tagIds.isEmpty()) {
            photoTagMapper.delete(new LambdaQueryWrapper<PhotoTag>()
                    .eq(PhotoTag::getPhotoId, photoId)
                    .in(PhotoTag::getTagId, tagIds));
        }
    }

    private void linkTag(Photo photo, String type, String name) {
        linkTag(photo, type, canonicalName(type, name), defaultConfidence(type));
    }

    private void linkTag(Photo photo, String type, String name, BigDecimal confidence) {
        if (name == null || name.isBlank()) return;
        Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, photo.getUserId())
                .eq(Tag::getTagType, type)
                .eq(Tag::getName, name));
        if (tag == null) {
            tag = new Tag();
            tag.setUserId(photo.getUserId());
            tag.setTagType(type);
            tag.setName(name);
            tag.setCreateTime(LocalDateTime.now());
            try {
                tagMapper.insert(tag);
            } catch (DuplicateKeyException ignored) {
                tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getUserId, photo.getUserId())
                        .eq(Tag::getTagType, type)
                        .eq(Tag::getName, name));
            }
        }
        PhotoTag link = new PhotoTag();
        link.setUserId(photo.getUserId());
        link.setPhotoId(photo.getId());
        link.setTagId(tag.getId());
        link.setConfidence(confidence);
        link.setCreateTime(LocalDateTime.now());
        try {
            photoTagMapper.insert(link);
        } catch (DuplicateKeyException ignored) {
            // A retry can race with an already completed analysis; the unique key keeps one link.
        }
    }

    private BigDecimal defaultConfidence(String type) {
        return switch (type) {
            case "dominant_color", "background_color", "foreground_color" -> new BigDecimal("0.85");
            case "subject", "scene" -> new BigDecimal("0.82");
            default -> new BigDecimal("0.75");
        };
    }

    private String canonicalName(String type, String name) {
        String value = name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
        if ("subject".equals(type) && Set.of("people", "human", "tourist", "visitor").contains(value)) {
            return "person";
        }
        if ("mood".equals(type) && Set.of("quiet", "serene", "peaceful", "tranquil", "silent").contains(value)) {
            return "calm";
        }
        return value;
    }
}
