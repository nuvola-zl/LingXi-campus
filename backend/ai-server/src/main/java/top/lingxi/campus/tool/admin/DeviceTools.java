package top.lingxi.campus.tool.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import top.lingxi.campus.admin.user.service.DeviceRequestService;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceRequest;
import top.lingxi.campus.tool.DuplicateChecker;
import top.lingxi.campus.tool.ToolCallGateway;

import java.util.Map;

/**
 * 后勤域工具：器材借用（相机/投影仪/运动器材等）
 *
 * 主题变更（灵犀校园）：办公设备申领 → 校园器材借用，仅显示文案变更
 *
 * 身份安全修复：用户 ID 通过 ToolContext 注入（见 tool.hr.HrTools 类注释）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTools {

    private final DeviceRequestService deviceRequestService;
    private final DuplicateChecker duplicateChecker;
    private final ToolCallGateway toolGateway;

    @Tool(description = "借用校园器材物资。学生想借相机、投影仪、体育器材、活动物资时调用，" +
            "支持的器材类型由仓库库存决定，常见如：camera(相机)、projector(投影仪)、" +
            "sports_kit(运动器材)、tent(帐篷)等")
    public String requestDevice(
            ToolContext toolContext,
            @ToolParam(description = "器材类型：camera/projector/sports_kit 等，以库存为准") String deviceType,
            @ToolParam(description = "借用原因") String reason) {

        Long realUserId = resolveUserId(toolContext);
        return toolGateway.execute("requestDevice", Map.of("deviceType", deviceType),
                () -> doRequestDevice(realUserId, deviceType, reason));
    }

    private String doRequestDevice(Long userId, String deviceType, String reason) {
        String dupMsg = duplicateChecker.checkDuplicateDeviceRequest(userId, deviceType);
        if (dupMsg != null) {
            log.warn("拦截重复器材借用: userId={}, deviceType={}", userId, deviceType);
            return dupMsg;
        }

        AdminDeviceRequest request = deviceRequestService.submitRequest(userId, deviceType, reason);

        if (request.getStockStatus() == 1) {
            // 有库存：同步分配
            deviceRequestService.allocateDevice(request.getRequestNo());
            return String.format(
                    "✅ 借用单 %s 已提交并完成分配，器材已就绪。\n" +
                            "请前往器材室领取，后勤老师确认后流程完成。\n" +
                            "您可随时输入「查询 %s」查看进度。",
                    request.getRequestNo(), request.getRequestNo()
            );
        }

        // 无库存：同步创建购置申请，等待后勤审批+到货
        String orderNo = deviceRequestService.createPurchaseOrder(request.getRequestNo());
        return String.format(
                "📋 借用单 %s 已提交，当前仓库无库存，已自动向后勤提交购置申请（单号 %s）：\n" +
                        "后勤审批 → 到货入库 → 系统自动分配 → 待领取\n" +
                        "⏱️ 预计需要 3-5 个工作日，到货后无需您重复提交。\n" +
                        "您可随时输入「查询 %s」查看进度。",
                request.getRequestNo(), orderNo, request.getRequestNo()
        );
    }

    @Tool(description = "查询器材借用进度")
    public String queryDeviceStatus(
            @ToolParam(description = "借用单号") String requestNo) {

        return toolGateway.execute("queryDeviceStatus", Map.of("requestNo", requestNo),
                () -> doQueryDeviceStatus(requestNo));
    }

    private String doQueryDeviceStatus(String requestNo) {
        AdminDeviceRequest request = deviceRequestService.getByRequestNo(requestNo);
        if (request == null) return "❌ 单号不存在，请检查单号是否正确。";

        String[] statusMap = {"", "待处理", "购置中", "准备中", "待领取", "已完成", "已取消", "处理失败"};
        String stockStatus = request.getStockStatus() == 1 ? "有库存" : "无库存（购置中）";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📋 借用单号：%s\n", requestNo));
        sb.append(String.format("📌 当前状态：%s\n", statusMap[request.getStatus()]));
        sb.append(String.format("📦 库存情况：%s\n", stockStatus));

        if (request.getAllocatedDeviceId() != null) {
            sb.append(String.format("🔧 已分配器材ID：%s\n", request.getAllocatedDeviceId()));
        }

        if (request.getPurchaseOrderNo() != null) {
            sb.append(String.format("🛒 关联购置单：%s\n", request.getPurchaseOrderNo()));
        }

        switch (request.getStatus()) {
            case 2 -> sb.append("💡 提示：后勤审批中，请耐心等待到货通知。");
            case 3 -> sb.append("💡 提示：即将分配器材，请稍候。");
            case 4 -> sb.append("💡 提示：器材已就绪，请前往器材室领取。");
            case 5 -> sb.append("💡 提示：流程已完成，如有问题请联系后勤老师。");
            case 6 -> sb.append("💡 提示：处理失败，请联系后勤老师排查原因。");
        }

        return sb.toString();
    }

    /**
     * 从工具调用上下文解析用户 ID（ToolContext 优先，BaseContext 兜底）
     */
    private Long resolveUserId(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext() != null) {
            Object uid = toolContext.getContext().get("userId");
            if (uid instanceof Long l) return l;
            if (uid instanceof Number n) return n.longValue();
        }
        return BaseContext.getCurrentId();
    }
}