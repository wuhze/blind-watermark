package com.blindwatermark.common;

import java.io.Serial;

/**
 * 自定义业务异常
 * 在业务逻辑中抛出，UI层捕获后向用户弹出友好提示
 */
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
