package top.lingxi.campus.admin.user.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import top.lingxi.campus.admin.user.service.DeviceRequestService;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.result.Result;

import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceRequest;

@RestController("adminDeviceRequestController")
@RequestMapping("/admin/device")
@RequiredArgsConstructor
public class DeviceRequestController {

    private final DeviceRequestService deviceRequestService;
    
    @PostMapping("/apply")
    public Result<AdminDeviceRequest> apply(@RequestBody ApplyDTO dto) {
        Long userId= BaseContext.getCurrentId();
        AdminDeviceRequest request = deviceRequestService.submitRequest(
            userId,
            dto.getDeviceType(),
            dto.getReason()
        );
        return Result.success(request);
    }
    
    @GetMapping("/status/{requestNo}")
    public Result<AdminDeviceRequest> status(@PathVariable String requestNo) {
        return Result.success(deviceRequestService.getByRequestNo(requestNo));
    }
    
    @Data
    public static class ApplyDTO {
        private String deviceType;
        private String reason;
    }
}