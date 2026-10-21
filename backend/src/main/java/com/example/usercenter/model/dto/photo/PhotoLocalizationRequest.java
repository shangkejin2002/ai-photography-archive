package com.example.usercenter.model.dto.photo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PhotoLocalizationRequest(
        @NotEmpty @Size(max = 20) List<Long> photoIds
) {
}
