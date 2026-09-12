package top.lingxi.campus.common.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import top.lingxi.campus.common.result.NodeResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class DagContext {

    private String requestNo;
    private Map<String, Object> params;
    private Map<String, NodeResult> results;

    private static final ObjectMapper mapper = new ObjectMapper();

    public DagContext(String requestNo, Map<String, Object> params) {
        this.requestNo = requestNo;
        this.params = params != null ? params : new HashMap<>();
        this.results = new ConcurrentHashMap<>();
    }
    
    public void putResult(String nodeId, NodeResult result) {
        results.put(nodeId, result);
    }
    
    public NodeResult getResult(String nodeId) {
        return results.get(nodeId);
    }
    
    /**
     * 转为 JSON 字符串（用于持久化）
     */
    public String toJson() {
        try {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("requestNo", requestNo);
            snapshot.put("params", params);
            // results 里的 NodeResult 需要简化序列化
            Map<String, Object> simpleResults = new HashMap<>();
            for (Map.Entry<String, NodeResult> entry : results.entrySet()) {
                NodeResult nodeResult = entry.getValue();
                if (nodeResult == null) continue;
                Map<String, Object> r = new HashMap<>();
                r.put("success", nodeResult.isSuccess());
                r.put("data", nodeResult.getData());
                r.put("skipped", nodeResult.isSkipped());
                simpleResults.put(entry.getKey(), r);
            }
            snapshot.put("results", simpleResults);
            return mapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}