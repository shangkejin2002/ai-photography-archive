package com.example.usercenter.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioIntentResult(
        Integer year,
        String season,
        String location,
        Integer iso,
        Boolean favoriteOnly,
        String cameraModel,
        String lensModel,
        List<String> subjects,
        List<String> colors,
        List<String> visualTags,
        List<String> excludeTags,
        Boolean diversityRequested,
        String orientation,
        String shutterSpeed,
        BigDecimal aperture,
        BigDecimal focalLength,
        Boolean hasPeople,
        Integer peopleCount,
        List<String> backgroundColors,
        List<String> moods,
        List<String> requiredTags,
        List<String> preferredTags,
        Integer limit,
        String sort,
        String suggestedTitle,
        String suggestedSubtitle
) {
}
