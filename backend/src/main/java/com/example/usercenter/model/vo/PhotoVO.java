package com.example.usercenter.model.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PhotoVO(
        Long id,
        String originalFilename,
        String originalUrl,
        String thumbnailUrl,
        String contentType,
        Long fileSize,
        Integer width,
        Integer height,
        Boolean favorite,
        String cameraMake,
        String cameraModel,
        String lensModel,
        BigDecimal focalLength,
        Integer focalLength35mm,
        BigDecimal aperture,
        String shutterSpeed,
        Integer isoValue,
        LocalDateTime takenAt,
        Integer orientation,
        BigDecimal exposureBias,
        String meteringMode,
        String whiteBalance,
        String flashStatus,
        BigDecimal gpsLatitude,
        BigDecimal gpsLongitude,
        String locationName,
        String metadataStatus,
        String metadataError,
        String aiDescription,
        String localizedAiDescription,
        List<PhotoTagVO> aiTags,
        String analysisStatus,
        String analysisError,
        String analysisModel,
        String analysisVersion,
        Integer analysisAttempts,
        LocalDateTime analyzedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
