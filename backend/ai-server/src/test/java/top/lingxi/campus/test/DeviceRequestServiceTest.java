package top.lingxi.campus.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.lingxi.campus.admin.user.service.DeviceRequestService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class DeviceRequestServiceTest {
    
    @Autowired
    private DeviceRequestService deviceRequestService;
    
    @Test
    public void testAllocateDevice_concurrent() throws InterruptedException {
        String requestNo = "DEV-20260722-001";
        
        // 模拟 10 个线程同时分配
        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    deviceRequestService.allocateDevice(requestNo);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        latch.await(10, TimeUnit.SECONDS);
        
        // 只有 1 个成功，其他都报"库存不足"或"无可用设备"
        assertEquals(1, successCount.get());
        assertEquals(9, failCount.get());
    }
}