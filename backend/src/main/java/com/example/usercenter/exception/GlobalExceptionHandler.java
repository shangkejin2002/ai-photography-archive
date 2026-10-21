package com.example.usercenter.exception;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.i18n.AppMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final AppMessages messages;

    public GlobalExceptionHandler(AppMessages messages) {
        this.messages = messages;
    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        String description = messages.get(e.getErrorKey(), e.getErrorArgs());
        return ResultUtils.error(
                e.getCode(),
                messages.get(genericKey(e.getCode())),
                description,
                e.getErrorKey(),
                java.util.List.of(e.getErrorArgs())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> validationExceptionHandler(MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(org.springframework.validation.FieldError::getField)
                .orElse("request");
        String key = "error.validation";
        return ResultUtils.error(
                ErrorCode.PARAMS_ERROR.getCode(),
                messages.get(ErrorCode.PARAMS_ERROR.getMessageKey()),
                messages.get(key, field),
                key,
                java.util.List.of(field)
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public BaseResponse<?> maxUploadSizeExceededExceptionHandler(MaxUploadSizeExceededException e) {
        return ResultUtils.error(
                ErrorCode.PARAMS_ERROR.getCode(),
                messages.get(ErrorCode.PARAMS_ERROR.getMessageKey()),
                messages.get("error.photo.tooLarge"),
                "error.photo.tooLarge",
                java.util.List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public BaseResponse<?> systemExceptionHandler(Exception e) {
        log.error("system error", e);
        String key = ErrorCode.SYSTEM_ERROR.getMessageKey();
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR.getCode(), messages.get(key), messages.get(key), key, java.util.List.of());
    }

    private String genericKey(int code) {
        for (ErrorCode value : ErrorCode.values()) if (value.getCode() == code) return value.getMessageKey();
        return ErrorCode.SYSTEM_ERROR.getMessageKey();
    }
}
