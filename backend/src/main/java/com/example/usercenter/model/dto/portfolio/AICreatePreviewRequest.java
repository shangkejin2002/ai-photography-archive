package com.example.usercenter.model.dto.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AICreatePreviewRequest(
        @NotBlank(message = "请输入想要寻找的照片") @Size(max = 1000) String prompt
) {
}
