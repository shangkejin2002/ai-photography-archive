package com.example.usercenter.analysis;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PhotoAnalysisDispatcher {

    private final PhotoAnalysisService photoAnalysisService;

    public PhotoAnalysisDispatcher(PhotoAnalysisService photoAnalysisService) {
        this.photoAnalysisService = photoAnalysisService;
    }

    @Async("photoAnalysisExecutor")
    public void analyzeAsync(Long photoId) {
        photoAnalysisService.analyze(photoId);
    }
}
