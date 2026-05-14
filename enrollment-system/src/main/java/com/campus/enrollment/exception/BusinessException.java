package com.campus.enrollment.exception;

/**
 * 业务异常：携带业务状态码，由全局异常处理器转换为统一 JSON。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
