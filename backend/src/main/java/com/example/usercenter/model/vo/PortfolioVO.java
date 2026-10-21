package com.example.usercenter.model.vo;

import java.time.LocalDateTime;
import java.util.List;

public record PortfolioVO(
        Long id,
        String title,
        String subtitle,
        String description,
        String sourcePrompt,
        String status,
        Long coverPhotoId,
        String coverThumbnailUrl,
        Boolean shareEnabled,
        String shareToken,
        String shareUrl,
        LocalDateTime sharedAt,
        List<PhotoVO> photos,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
