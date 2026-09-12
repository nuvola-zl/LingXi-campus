package top.lingxi.campus.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import top.lingxi.campus.admin.user.controller.DeviceRequestController;
import top.lingxi.campus.common.result.Result;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class DeviceRequestIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    public void testApplyDevice_withStock() throws Exception {
        // 1. 提交申领
        DeviceRequestController.ApplyDTO dto = new DeviceRequestController.ApplyDTO();
        dto.setUserId(1L);
        dto.setDeviceType("macbook_pro");
        dto.setReason("开发需要");
        
        String response = mockMvc.perform(post("/dag/device/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        
        Result<AdminDeviceRequest> result = objectMapper.readValue(response, new TypeReference<>() {});
        String requestNo = result.getData().getRequestNo();
        
        // 2. 等待 DAG 执行（异步，等几秒）
        Thread.sleep(3000);
        
        // 3. 查询状态
        String statusResponse = mockMvc.perform(get("/dag/device/status/" + requestNo))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        
        Result<AdminDeviceRequest> statusResult = objectMapper.readValue(statusResponse, new TypeReference<>() {});
        
        // 4. 验证：状态应该是 4(待领取) 或 5(已完成)
        assertTrue(statusResult.getData().getStatus() >= 4);
        assertNotNull(statusResult.getData().getAllocatedDeviceId());
    }
}