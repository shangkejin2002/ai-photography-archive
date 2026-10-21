package com.example.usercenter.common;

public enum ErrorCode {

    PARAMS_ERROR(40000, "请求参数错误", "error.params"),
    NOT_LOGIN(40100, "未登录", "error.notLogin"),
    NO_AUTH(40101, "无权限", "error.noAuth"),
    NOT_FOUND(40400, "数据不存在", "error.notFound"),
    SYSTEM_ERROR(50000, "系统内部异常", "error.system");

    private final int code;
    private final String message;
    private final String messageKey;

    ErrorCode(int code, String message, String messageKey) {
        this.code = code;
        this.message = message;
        this.messageKey = messageKey;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
