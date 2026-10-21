package com.example.usercenter.storage;

public record StoredPhoto(
        String originalFilename,
        String originalStorageKey,
        String thumbnailStorageKey,
        String contentType,
        long fileSize,
        int width,
        int height
) {
}
