package com.example.usercenter.analysis;

import java.util.List;

public record PhotoAnalysisResult(
        List<String> scenes,
        List<String> subjects,
        List<String> visualElements,
        List<String> colors,
        List<String> lighting,
        List<String> weather,
        List<String> compositions,
        List<String> visualSeasons,
        List<String> dominantColors,
        List<String> backgroundColors,
        List<String> foregroundColors,
        List<String> moods,
        List<String> activities,
        List<String> depthCues,
        Boolean peoplePresent,
        Integer peopleCount,
        String peopleProminence,
        List<String> tags,
        String description,
        String descriptionJa
) {
}
