package com.example.usercenter.common;

import java.util.List;

public record BaseResponse<T>(
        int code,
        T data,
        String message,
        String description,
        String errorKey,
        List<Object> errorArgs
) {
}
