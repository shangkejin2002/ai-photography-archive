package com.example.usercenter.model.vo;

import java.util.List;

public record ShootingInsightsVO(
        long totalPhotos,
        long favoritePhotos,
        long exifPhotos,
        long aiIndexedPhotos,
        long gpsPhotos,
        long namedLocationPhotos,
        int exifCoverage,
        int aiCoverage,
        int gpsCoverage,
        List<MonthlyCountVO> monthlyCounts,
        List<InsightItemVO> cameras,
        List<InsightItemVO> lenses,
        List<InsightItemVO> apertures,
        List<InsightItemVO> isoValues,
        List<InsightItemVO> focalRanges,
        List<InsightItemVO> locations,
        List<InsightItemVO> subjects,
        List<InsightItemVO> dominantColors,
        List<InsightItemVO> moods,
        List<InsightItemVO> lighting,
        String observation,
        List<String> observationDetails
) {
}
