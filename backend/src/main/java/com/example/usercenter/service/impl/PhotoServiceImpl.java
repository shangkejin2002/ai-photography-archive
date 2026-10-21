package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.usercenter.analysis.PhotoAnalysisDispatcher;
import com.example.usercenter.analysis.PhotoAnalysisPersistenceService;
import com.example.usercenter.analysis.PhotoAnalysisService;
import com.example.usercenter.analysis.DeepSeekPhotoDescriptionTranslator;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.PhotoMapper;
import com.example.usercenter.mapper.PhotoTagMapper;
import com.example.usercenter.mapper.TagMapper;
import com.example.usercenter.metadata.PhotoMetadata;
import com.example.usercenter.metadata.PhotoMetadataService;
import com.example.usercenter.model.entity.Photo;
import com.example.usercenter.model.entity.PhotoTag;
import com.example.usercenter.model.entity.Tag;
import com.example.usercenter.model.vo.PhotoTagVO;
import com.example.usercenter.model.vo.PhotoVO;
import com.example.usercenter.service.PhotoService;
import com.example.usercenter.storage.PhotoStorageService;
import com.example.usercenter.storage.ImageDimensions;
import com.example.usercenter.storage.StoredPhoto;
import org.springframework.core.io.Resource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PhotoServiceImpl extends ServiceImpl<PhotoMapper, Photo> implements PhotoService {

    private static final String ORIGINAL_VARIANT = "original";
    private static final String THUMBNAIL_VARIANT = "thumbnail";
    private static final String METADATA_PENDING = "PENDING";

    private final PhotoStorageService photoStorageService;
    private final PhotoMetadataService photoMetadataService;
    private final PhotoAnalysisService photoAnalysisService;
    private final PhotoAnalysisDispatcher photoAnalysisDispatcher;
    private final DeepSeekPhotoDescriptionTranslator descriptionTranslator;
    private final PhotoTagMapper photoTagMapper;
    private final TagMapper tagMapper;

    public PhotoServiceImpl(
            PhotoStorageService photoStorageService,
            PhotoMetadataService photoMetadataService,
            PhotoAnalysisService photoAnalysisService,
            PhotoAnalysisDispatcher photoAnalysisDispatcher,
            DeepSeekPhotoDescriptionTranslator descriptionTranslator,
            PhotoTagMapper photoTagMapper,
            TagMapper tagMapper
    ) {
        this.photoStorageService = photoStorageService;
        this.photoMetadataService = photoMetadataService;
        this.photoAnalysisService = photoAnalysisService;
        this.photoAnalysisDispatcher = photoAnalysisDispatcher;
        this.descriptionTranslator = descriptionTranslator;
        this.photoTagMapper = photoTagMapper;
        this.tagMapper = tagMapper;
    }

    @Override
    @Transactional
    public PhotoVO upload(Long userId, MultipartFile file) {
        PhotoMetadata metadata;
        try {
            metadata = photoMetadataService.extract(file.getInputStream());
        } catch (IOException e) {
            metadata = PhotoMetadata.failed("无法读取照片元数据");
        }
        StoredPhoto stored = photoStorageService.store(userId, file, metadata.orientation());
        try {
            LocalDateTime now = LocalDateTime.now();
            Photo photo = new Photo();
            photo.setUserId(userId);
            photo.setOriginalFilename(stored.originalFilename());
            photo.setOriginalUrl("pending");
            photo.setThumbnailUrl("pending");
            photo.setOriginalStorageKey(stored.originalStorageKey());
            photo.setThumbnailStorageKey(stored.thumbnailStorageKey());
            photo.setContentType(stored.contentType());
            photo.setFileSize(stored.fileSize());
            photo.setWidth(stored.width());
            photo.setHeight(stored.height());
            photo.setFavorite(0);
            applyMetadata(photo, metadata);
            photo.setAnalysisStatus(photoAnalysisService.isConfigured() ? "PENDING" : "FAILED");
            photo.setAnalysisError(photoAnalysisService.isConfigured()
                    ? null
                    : "DeepSeek 尚未配置。请设置 DEEPSEEK_API_KEY 后重试。");
            photo.setAnalysisAttempts(0);
            photo.setCreateTime(now);
            photo.setUpdateTime(now);
            photo.setIsDelete(0);

            if (!save(photo)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "照片记录创建失败");
            }

            photo.setOriginalUrl(contentUrl(photo.getId(), ORIGINAL_VARIANT));
            photo.setThumbnailUrl(contentUrl(photo.getId(), THUMBNAIL_VARIANT));
            photo.setUpdateTime(LocalDateTime.now());
            if (!updateById(photo)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "照片记录更新失败");
            }
            if (photoAnalysisService.isConfigured()) {
                dispatchAfterCommit(photo.getId());
            }
            return toVO(photo);
        } catch (RuntimeException e) {
            photoStorageService.delete(stored.originalStorageKey());
            photoStorageService.delete(stored.thumbnailStorageKey());
            throw e;
        }
    }

    @Override
    public List<PhotoVO> listOwned(Long userId, Boolean favorite) {
        return lambdaQuery()
                .eq(Photo::getUserId, userId)
                .eq(favorite != null, Photo::getFavorite, Boolean.TRUE.equals(favorite) ? 1 : 0)
                .orderByDesc(Photo::getCreateTime)
                .orderByDesc(Photo::getId)
                .list()
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public PhotoVO getOwned(Long userId, Long photoId) {
        Photo photo = requireOwnedPhoto(userId, photoId);
        if (METADATA_PENDING.equals(photo.getMetadataStatus())) {
            reprocessPhotoMetadata(photo);
        }
        return toVO(photo);
    }

    @Override
    @Transactional
    public PhotoVO setFavorite(Long userId, Long photoId, boolean favorite) {
        Photo photo = requireOwnedPhoto(userId, photoId);
        photo.setFavorite(favorite ? 1 : 0);
        photo.setUpdateTime(LocalDateTime.now());
        if (!updateById(photo)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "收藏状态更新失败");
        }
        return toVO(photo);
    }

    @Override
    @Transactional
    public PhotoVO setLocationName(Long userId, Long photoId, String locationName) {
        Photo photo = requireOwnedPhoto(userId, photoId);
        String normalized = locationName == null ? null : locationName.trim();
        photo.setLocationName(normalized == null || normalized.isBlank() ? null : normalized);
        photo.setUpdateTime(LocalDateTime.now());
        if (!updateById(photo)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "地点名称更新失败");
        }
        return toVO(photo);
    }

    @Override
    public int reprocessMetadata(Long userId) {
        List<Photo> photos = lambdaQuery()
                .eq(Photo::getUserId, userId)
                .in(Photo::getMetadataStatus, "PENDING", "FAILED")
                .list();
        int processed = 0;
        for (Photo photo : photos) {
            reprocessPhotoMetadata(photo);
            processed += 1;
        }
        return processed;
    }

    @Override
    @Transactional
    public PhotoVO requestAnalysis(Long userId, Long photoId) {
        Photo photo = requireOwnedPhoto(userId, photoId);
        if ("ANALYZING".equals(photo.getAnalysisStatus())) return toVO(photo);
        photo.setAnalysisStatus("PENDING");
        photo.setAnalysisError(null);
        photo.setUpdateTime(LocalDateTime.now());
        if (!updateById(photo)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法提交 AI 分析");
        }
        dispatchAfterCommit(photo.getId());
        return toVO(photo);
    }

    @Override
    @Transactional
    public int requestPendingAnalyses(Long userId) {
        List<Photo> photos = lambdaQuery()
                .eq(Photo::getUserId, userId)
                .in(Photo::getAnalysisStatus, "PENDING", "FAILED")
                .list();
        for (Photo photo : photos) {
            photo.setAnalysisStatus("PENDING");
            photo.setAnalysisError(null);
            photo.setUpdateTime(LocalDateTime.now());
            updateById(photo);
            dispatchAfterCommit(photo.getId());
        }
        return photos.size();
    }

    @Override
    public synchronized List<PhotoVO> localizeJapaneseDescriptions(Long userId, List<Long> photoIds) {
        List<Long> ids = photoIds == null ? List.of() : photoIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(20)
                .toList();
        if (ids.isEmpty()) return List.of();
        List<Photo> photos = ids.stream().map(id -> requireOwnedPhoto(userId, id)).toList();
        Map<Long, String> missing = new LinkedHashMap<>();
        for (Photo photo : photos) {
            if (hasText(photo.getAiDescription()) && !hasText(photo.getAiDescriptionJa())) {
                missing.put(photo.getId(), photo.getAiDescription());
            }
        }
        if (!missing.isEmpty()) {
            Map<Long, String> translations = descriptionTranslator.translate(missing);
            for (Photo photo : photos) {
                String translated = translations.get(photo.getId());
                if (hasText(translated) && !hasText(photo.getAiDescriptionJa())) {
                    photo.setAiDescriptionJa(translated);
                    photo.setAiDescriptionJaVersion("translated-v1");
                    photo.setUpdateTime(LocalDateTime.now());
                    updateById(photo);
                }
            }
        }
        return photos.stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public boolean deleteOwned(Long userId, Long photoId) {
        Photo photo = requireOwnedPhoto(userId, photoId);
        boolean removed = removeById(photo.getId());
        if (!removed) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "照片删除失败");
        }
        photoStorageService.delete(photo.getOriginalStorageKey());
        photoStorageService.delete(photo.getThumbnailStorageKey());
        return true;
    }

    @Override
    public PhotoContent loadContent(Long userId, Long photoId, String variant) {
        Photo photo = requireOwnedPhoto(userId, photoId);
        if (ORIGINAL_VARIANT.equalsIgnoreCase(variant)) {
            return new PhotoContent(
                    photoStorageService.load(photo.getOriginalStorageKey()),
                    photo.getContentType()
            );
        }
        if (THUMBNAIL_VARIANT.equalsIgnoreCase(variant)) {
            return new PhotoContent(
                    photoStorageService.load(photo.getThumbnailStorageKey()),
                    "image/jpeg"
            );
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的照片尺寸类型");
    }

    private Photo requireOwnedPhoto(Long userId, Long photoId) {
        if (photoId == null || photoId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "照片 ID 无效");
        }
        Photo photo = lambdaQuery()
                .eq(Photo::getId, photoId)
                .eq(Photo::getUserId, userId)
                .one();
        if (photo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "照片不存在");
        }
        return photo;
    }

    private String contentUrl(Long photoId, String variant) {
        return "/api/photo/" + photoId + "/content?variant=" + variant;
    }

    private PhotoVO toVO(Photo photo) {
        String localizedDescription = "ja".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage())
                ? photo.getAiDescriptionJa()
                : photo.getAiDescription();
        return new PhotoVO(
                photo.getId(),
                photo.getOriginalFilename(),
                photo.getOriginalUrl(),
                photo.getThumbnailUrl(),
                photo.getContentType(),
                photo.getFileSize(),
                photo.getWidth(),
                photo.getHeight(),
                Integer.valueOf(1).equals(photo.getFavorite()),
                photo.getCameraMake(),
                photo.getCameraModel(),
                photo.getLensModel(),
                photo.getFocalLength(),
                photo.getFocalLength35mm(),
                photo.getAperture(),
                photo.getShutterSpeed(),
                photo.getIsoValue(),
                photo.getTakenAt(),
                photo.getOrientation(),
                photo.getExposureBias(),
                photo.getMeteringMode(),
                photo.getWhiteBalance(),
                photo.getFlashStatus(),
                photo.getGpsLatitude(),
                photo.getGpsLongitude(),
                photo.getLocationName(),
                photo.getMetadataStatus(),
                photo.getMetadataError(),
                photo.getAiDescription(),
                localizedDescription,
                loadAiTags(photo.getId()),
                photo.getAnalysisStatus(),
                photo.getAnalysisError(),
                photo.getAnalysisModel(),
                photo.getAnalysisVersion(),
                photo.getAnalysisAttempts(),
                photo.getAnalyzedAt(),
                photo.getCreateTime(),
                photo.getUpdateTime()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<PhotoTagVO> loadAiTags(Long photoId) {
        List<PhotoTag> links = photoTagMapper.selectList(new LambdaQueryWrapper<PhotoTag>()
                .eq(PhotoTag::getPhotoId, photoId));
        if (links.isEmpty()) return List.of();
        List<Long> tagIds = links.stream().map(PhotoTag::getTagId).toList();
        java.util.Map<Long, java.math.BigDecimal> confidenceByTag = new java.util.HashMap<>();
        links.forEach(link -> confidenceByTag.put(link.getTagId(), link.getConfidence()));
        return tagMapper.selectBatchIds(tagIds).stream()
                .filter(tag -> PhotoAnalysisPersistenceService.AI_TAG_TYPES.contains(tag.getTagType()))
                .map(tag -> new PhotoTagVO(tag.getName(), tag.getTagType(), confidenceByTag.get(tag.getId())))
                .toList();
    }

    private void dispatchAfterCommit(Long photoId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    photoAnalysisDispatcher.analyzeAsync(photoId);
                }
            });
        } else {
            photoAnalysisDispatcher.analyzeAsync(photoId);
        }
    }

    private void reprocessPhotoMetadata(Photo photo) {
        PhotoMetadata metadata;
        try (InputStream input = photoStorageService.load(photo.getOriginalStorageKey()).getInputStream()) {
            metadata = photoMetadataService.extract(input);
        } catch (IOException e) {
            metadata = PhotoMetadata.failed("无法读取原始照片");
        }
        applyMetadata(photo, metadata);
        ImageDimensions dimensions = photoStorageService.refreshThumbnail(
                photo.getOriginalStorageKey(),
                photo.getThumbnailStorageKey(),
                metadata.orientation()
        );
        photo.setWidth(dimensions.width());
        photo.setHeight(dimensions.height());
        photo.setUpdateTime(LocalDateTime.now());
        if (!updateById(photo)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "照片元数据更新失败");
        }
    }

    private void applyMetadata(Photo photo, PhotoMetadata metadata) {
        photo.setCameraMake(metadata.cameraMake());
        photo.setCameraModel(metadata.cameraModel());
        photo.setLensModel(metadata.lensModel());
        photo.setFocalLength(metadata.focalLength());
        photo.setFocalLength35mm(metadata.focalLength35mm());
        photo.setAperture(metadata.aperture());
        photo.setShutterSpeed(metadata.shutterSpeed());
        photo.setIsoValue(metadata.isoValue());
        photo.setTakenAt(metadata.takenAt());
        photo.setOrientation(metadata.orientation());
        photo.setExposureBias(metadata.exposureBias());
        photo.setMeteringMode(metadata.meteringMode());
        photo.setWhiteBalance(metadata.whiteBalance());
        photo.setFlashStatus(metadata.flashStatus());
        photo.setGpsLatitude(metadata.gpsLatitude());
        photo.setGpsLongitude(metadata.gpsLongitude());
        photo.setMetadataStatus(metadata.status());
        photo.setMetadataError(metadata.error());
    }
}
