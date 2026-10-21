package com.example.usercenter.metadata;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PhotoMetadata(
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
        String status,
        String error
) {
    public static PhotoMetadata failed(String error) {
        return new PhotoMetadata(
                null, null, null, null, null, null, null, null, null,
                1, null, null, null, null, null, null, "FAILED", error
        );
    }
}
