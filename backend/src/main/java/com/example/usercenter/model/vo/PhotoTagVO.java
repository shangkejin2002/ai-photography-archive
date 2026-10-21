package com.example.usercenter.model.vo;

import java.math.BigDecimal;

public record PhotoTagVO(
        String name,
        String type,
        BigDecimal confidence
) {
}
