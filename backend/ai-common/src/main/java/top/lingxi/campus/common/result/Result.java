package top.lingxi.campus.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果
 * @param <T>
 */
@Data
public class Result<T> implements Serializable {

    private Integer code;       // 编码：200成功，其它为失败（兼容前端数字契约）
    private String errorCode;   // 业务错误码（如 ASTRA_001），用于排查定位
    private String msg;         // 错误信息
    private T data;             // 数据

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.code = 200;
        return result;
    }

    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<>();
        result.data = object;
        result.code = 200;
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.code = 0;
        result.msg = msg;
        return result;
    }

    /**
     * 业务错误：code 支持数字错误码（400/500）与字符串业务码（ASTRA_001）两种。
     * 字符串业务码统一映射为 HTTP 语义 400，原始业务码保留在 errorCode 字段，
     * 修复点：原实现 Integer.parseInt("ASTRA_001") 直接抛 NumberFormatException，
     * 导致全局异常处理器自身崩溃，业务错误永远变成 500。
     */
    public static <T> Result<T> error(String code, String msg) {
        Result<T> result = new Result<>();
        result.errorCode = code;
        try {
            result.code = Integer.parseInt(code);
        } catch (NumberFormatException e) {
            result.code = 400;
        }
        result.msg = msg;
        return result;
    }
}