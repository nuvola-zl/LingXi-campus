package top.lingxi.campus.test;

import org.springframework.http.MediaType;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceRequest;

@Test
public void testApplyDevice_noStock() throws Exception {
    // 1. 先把库存清 0（手动改数据库或调接口）
    
    // 2. 提交申领
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
    
    // 3. 验证状态：2(采购中)
    Thread.sleep(1000);
    String status1 = mockMvc.perform(get("/dag/device/status/" + requestNo))
        .andReturn().getResponse().getContentAsString();
    assertTrue(status1.contains("\"status\":2"));
    
    // 4. 管理员审批采购单（调管理端接口）
    // ... 先查采购单号，然后审批
    
    // 5. 管理员确认到货
    String orderNo = result.getData().getPurchaseOrderNo();
    mockMvc.perform(post("/admin/manage/purchase/" + orderNo + "/arrived"))
        .andExpect(status().isOk());
    
    // 6. 等待 DAG 恢复执行
    Thread.sleep(3000);
    
    // 7. 验证最终状态：4(待领取) 或 5(已完成)
    String status2 = mockMvc.perform(get("/dag/device/status/" + requestNo))
        .andReturn().getResponse().getContentAsString();
    assertTrue(status2.contains("\"status\":4") || status2.contains("\"status\":5"));
}