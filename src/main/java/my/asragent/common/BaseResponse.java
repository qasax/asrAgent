package my.asragent.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import my.asragent.exception.ErrorCode;


import java.io.Serializable;

@Data
@Schema(description = "通用响应包装")
public class BaseResponse<T> implements Serializable {

    @Schema(description = "响应状态码（0 表示成功）", example = "0")
    private int code;

    @Schema(description = "响应数据")
    private T data;

    @Schema(description = "响应消息", example = "ok")
    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}