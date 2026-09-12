package top.lingxi.campus.common.exception;

/**
 * 错误码枚举
 */
public enum ErrorCode {

    // ========== 通用错误码 ==========
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    PARAM_INVALID(400, "参数不合法"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ========== Astra 错误码 ==========
    ASTRA_LIBRARY_NOT_FOUND("ASTRA_001", "知识库不存在"),
    ASTRA_MEDIA_NOT_FOUND("ASTRA_002", "媒体文件不存在"),
    ASTRA_UNSUPPORTED_FILE_TYPE("ASTRA_003", "不支持的文件格式"),
    ASTRA_FILE_SIZE_EXCEEDED("ASTRA_004", "文件大小超出限制"),
    ASTRA_SHA256_MISMATCH("ASTRA_005", "SHA256校验失败"),
    ASTRA_PARSE_FAILED("ASTRA_006", "解析失败"),
    ASTRA_SESSION_NOT_FOUND("ASTRA_007", "会话不存在"),
    ASTRA_LIBRARY_EMPTY("ASTRA_008", "知识库为空，无法问答"),
    ASTRA_DUPLICATE_FILE("ASTRA_009", "文件已存在"),
    ASTRA_UPLOAD_FAILED("ASTRA_010", "文件上传失败"),

    // ========== HR 模块错误码 ==========
    HR_LEAVE_DUPLICATE("HR_001", "该时间段已有请假申请"),
    HR_LEAVE_DATE_INVALID("HR_002", "请假日期不合法"),
    HR_LEAVE_TIME_EXPIRED("HR_003", "请假时段已过期"),
    HR_LEAVE_BALANCE_INSUFFICIENT("HR_004", "假期余额不足"),
    DUPLICATE_REQUEST("HR_005", "重复请求"),

    // ========== 设备申领错误码 ==========
    DEVICE_REQUEST_NOT_FOUND("DEV_001", "申领单不存在"),
    DEVICE_REQUEST_NO_PERMISSION("DEV_002", "无权操作该申领单"),
    DEVICE_REQUEST_STATUS_INVALID("DEV_003", "当前状态不允许该操作"),
    DEVICE_REQUEST_NOT_RECEIVABLE("DEV_004", "申领单未处于待领取状态"),

    // 新增 ↓
    DEVICE_REQUEST_CANCEL_RUNNING("DEV_005", "设备准备中，请联系管理员取消"),
    DEVICE_REQUEST_CANCEL_COMPLETED("DEV_006", "已完成的申领单无法取消，请走归还流程"),
    DEVICE_REQUEST_RETURN_NOT_COMPLETED("DEV_007", "只有已完成的申领单才能归还"),
    DEVICE_RETURN_RECORD_NOT_FOUND("DEV_008", "归还记录不存在"),
    DEVICE_RETURN_ALREADY_PROCESSED("DEV_009", "归还记录已处理");



    private final String code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = String.valueOf(code);
        this.message = message;
    }

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}