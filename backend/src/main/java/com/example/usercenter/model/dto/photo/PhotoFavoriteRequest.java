package com.example.usercenter.model.dto.photo;

import jakarta.validation.constraints.NotNull;

public record PhotoFavoriteRequest(@NotNull Boolean favorite) {
}
