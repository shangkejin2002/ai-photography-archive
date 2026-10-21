package com.example.usercenter.model.dto.photo;

import jakarta.validation.constraints.Size;

public record PhotoLocationRequest(
        @Size(max = 512, message = "地点名称不能超过 512 个字符") String locationName
) {
}
