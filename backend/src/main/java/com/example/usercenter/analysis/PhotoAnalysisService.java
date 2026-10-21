package com.example.usercenter.analysis;

import com.example.usercenter.mapper.PhotoMapper;
import com.example.usercenter.model.entity.Photo;
import com.example.usercenter.storage.PhotoStorageService;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class PhotoAnalysisService {

    private final PhotoMapper photoMapper;
    private final PhotoStorageService photoStorageService;
    private final DeepSeekPhotoAnalysisClient client;
    private final PhotoAnalysisPersistenceService persistenceService;

    public PhotoAnalysisService(
            PhotoMapper photoMapper,
            PhotoStorageService photoStorageService,
            DeepSeekPhotoAnalysisClient client,
            PhotoAnalysisPersistenceService persistenceService
    ) {
        this.photoMapper = photoMapper;
        this.photoStorageService = photoStorageService;
        this.client = client;
        this.persistenceService = persistenceService;
    }

    public boolean isConfigured() {
        return client.isConfigured();
    }

    public void analyze(Long photoId) {
        Photo photo = photoMapper.selectById(photoId);
        if (photo == null || Integer.valueOf(1).equals(photo.getIsDelete())) return;
        if ("ANALYZING".equals(photo.getAnalysisStatus()) || "COMPLETED".equals(photo.getAnalysisStatus())) return;
        if (!client.isConfigured()) {
            persistenceService.fail(photo, "DeepSeek 尚未配置。请设置 DEEPSEEK_API_KEY 后重试。");
            return;
        }

        persistenceService.markAnalyzing(photo);
        try (InputStream input = photoStorageService.load(photo.getThumbnailStorageKey()).getInputStream()) {
            PhotoAnalysisResult result = client.analyze(input.readAllBytes());
            persistenceService.complete(photo, result, client.model());
        } catch (Exception e) {
            persistenceService.fail(photo, userSafeError(e));
        }
    }

    private String userSafeError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return "AI 分析失败，请稍后重试。";
        if (message.contains("HTTP 401")) return "DeepSeek API Key 无效，请检查配置后重试。";
        if (message.contains("HTTP 402")) return "DeepSeek 账户余额不足，请充值后重试。";
        if (message.contains("HTTP 429")) return "DeepSeek 请求频率过高，请稍后重试。";
        return "AI 分析失败：" + message.substring(0, Math.min(message.length(), 300));
    }
}
