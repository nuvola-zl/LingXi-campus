package top.lingxi.campus.common.result;

import lombok.Data;

@Data
public class NodeResult {
    private boolean success;
    private Object data;
    private String errorMsg;
    private boolean skipped;  // 新增：是否因已执行而跳过
    
    public static NodeResult ok(Object data) {
        NodeResult r = new NodeResult();
        r.setSuccess(true);
        r.setData(data);
        return r;
    }
    
    public static NodeResult ok() {
        return ok(null);
    }
    
    public static NodeResult fail(String msg) {
        NodeResult r = new NodeResult();
        r.setSuccess(false);
        r.setErrorMsg(msg);
        return r;
    }
    
    public static NodeResult skip() {  // 新增：幂等跳过
        NodeResult r = new NodeResult();
        r.setSuccess(true);
        r.setSkipped(true);
        return r;
    }
}