package com.campus.enrollment.common;

/**
 * 统一 API 响应体：成功与异常均使用相同 JSON 结构，便于前端处理。
 *
 * @param <T> 业务数据类型，失败时可为 null
 */
public class ApiResponse<T> {

    /** 业务状态码：200 成功，4xx 客户端，5xx 服务端 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 载荷数据 */
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构建成功响应。
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "操作成功", data);
    }

    /**
     * 构建成功响应（自定义提示）。
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    /**
     * 构建失败响应（无 data）。
     */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
