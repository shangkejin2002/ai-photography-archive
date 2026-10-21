package com.example.usercenter.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface PhotoStorageService {
    StoredPhoto store(Long userId, MultipartFile file, int orientation);

    ImageDimensions refreshThumbnail(String originalStorageKey, String thumbnailStorageKey, int orientation);

    Resource load(String storageKey);

    void delete(String storageKey);
}
