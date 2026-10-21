package com.example.usercenter.model.dto.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PortfolioUpdateRequest(
        @NotBlank(message = "标题不能为空") @Size(max = 256) String title,
        @Size(max = 512) String subtitle,
        @Size(max = 2000) String description,
        Long coverPhotoId,
        @NotEmpty(message = "至少选择一张照片") @Size(max = 20, message = "一个作品集最多 20 张照片") List<Long> photoIds
) {
}
