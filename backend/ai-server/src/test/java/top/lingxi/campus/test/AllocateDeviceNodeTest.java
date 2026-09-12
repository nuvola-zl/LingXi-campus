package top.lingxi.campus.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.lingxi.campus.ai.dag.service.AllocateDeviceNode;
import top.lingxi.campus.common.context.DagContext;
import top.lingxi.campus.common.result.NodeResult;

import java.util.HashMap;

@SpringBootTest
public class AllocateDeviceNodeTest {
    
    @Autowired
    private AllocateDeviceNode allocateDeviceNode;
    
    @Test
    public void testIdempotent() {
        DagContext context = new DagContext("DEV-20260722-001", new HashMap<>());
        
        // 第一次执行
        NodeResult r1 = allocateDeviceNode.execute(context);
        assertTrue(r1.isSuccess());
        
        // 第二次执行（幂等跳过）
        NodeResult r2 = allocateDeviceNode.execute(context);
        assertTrue(r2.isSkipped());
    }
}