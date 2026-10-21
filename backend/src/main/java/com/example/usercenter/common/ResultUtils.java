package com.example.usercenter.common;

public final class ResultUtils {

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok", null, null, null);
    }

    public static BaseResponse<?> error(ErrorCode errorCode) {
        return new BaseResponse<>(
                errorCode.getCode(),
                null,
                errorCode.getMessage(),
                null,
                errorCode.getMessageKey(),
                null
        );
    }

    public static BaseResponse<?> error(
            int code,
            String message,
            String description,
            String errorKey,
            java.util.List<Object> errorArgs
    ) {
        return new BaseResponse<>(
                code,
                null,
                message,
                description,
                errorKey,
                errorArgs
        );
    }
}
