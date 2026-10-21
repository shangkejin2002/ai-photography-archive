package com.example.usercenter.exception;

import com.example.usercenter.common.ErrorCode;

public class BusinessException extends RuntimeException {

    private final int code;
    private final String description;
    private final String errorKey;
    private final Object[] errorArgs;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.description = null;
        this.errorKey = errorCode.getMessageKey();
        this.errorArgs = new Object[0];
    }

    public BusinessException(
            ErrorCode errorCode,
            String description
    ) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.description = description;
        this.errorKey = LegacyErrorKeys.resolve(description, errorCode.getMessageKey());
        this.errorArgs = new Object[0];
    }

    private BusinessException(ErrorCode errorCode, String errorKey, Object[] errorArgs) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.description = null;
        this.errorKey = errorKey;
        this.errorArgs = errorArgs == null ? new Object[0] : errorArgs.clone();
    }

    public static BusinessException localized(ErrorCode errorCode, String errorKey, Object... errorArgs) {
        return new BusinessException(errorCode, errorKey, errorArgs);
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public Object[] getErrorArgs() {
        return errorArgs.clone();
    }
}
