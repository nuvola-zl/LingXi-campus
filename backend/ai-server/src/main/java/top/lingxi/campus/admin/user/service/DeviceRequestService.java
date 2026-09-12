package top.lingxi.campus.admin.user.service;

import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceRequest;

/**
 * 设备申领服务接口
 * <p>
 * 提供设备申领的全流程管理，支持有库存直接分配和无库存自动采购两种流程。
 * 各阶段由 DAG 引擎编排调用，状态流转如下：
 * </p>
 *
 * <p>申领状态说明：</p>
 * <ul>
 *   <li>2 = 采购中（无库存，需走采购流程）</li>
 *   <li>3 = 准备中（有库存，可直接分配）</li>
 *   <li>5 = 已完成</li>
 * </ul>
 *
 * <p>采购单状态说明：</p>
 * <ul>
 *   <li>1 = 待审批</li>
 *   <li>4 = 已到货</li>
 * </ul>
 *
 * <p>库存状态说明：</p>
 * <ul>
 *   <li>1 = 有库存</li>
 *   <li>2 = 无库存</li>
 * </ul>
 *
 * @author hazeaihub
 * @version 1.0
 */
public interface DeviceRequestService {

    /**
     * 提交设备申领
     * <p>
     * 用户提交设备申领请求，系统根据设备类型查询库存情况：
     * <ul>
     *   <li>有库存：状态置为"准备中"（3），后续由 DAG 引擎调用 {@link #allocateDevice} 分配设备</li>
     *   <li>无库存：状态置为"采购中"（2），后续由 DAG 引擎调用 {@link #createPurchaseOrder} 发起采购</li>
     * </ul>
     * 提交后自动生成唯一申领单号。
     * </p>
     *
     * @param userId     申领人用户 ID，不可为空
     * @param deviceType 设备类型，对应库存表中的 device_type 字段
     * @param reason     申领原因/备注说明
     * @return 提交成功的设备申领记录实体，包含生成的单号、库存状态及当前流程状态
     */
    @Transactional
    AdminDeviceRequest submitRequest(Long userId, String deviceType, String reason);

    /**
     * 根据采购单号查询设备申领记录
     * <p>
     * 通过关联的采购单号（purchase_order_no）反查对应的设备申领记录。
     * 通常用于采购到货后，根据采购单号找到原始申领单以继续后续流程。
     * </p>
     *
     * @param purchaseOrderNo 采购单号，不可为空
     * @return 关联的设备申领记录实体，若不存在则返回 null
     */
    AdminDeviceRequest getByPurchaseOrderNo(String purchaseOrderNo);

    /**
     * 分配设备
     * <p>
     * DAG 引擎节点调用。从在库设备中查找一台可用设备，将其分配给申领人，
     * 同时更新申领记录、扣减库存。
     * </p>
     *
     * @param requestNo 申领单号，不可为空
     * @throws RuntimeException 若无可用设备（库存数据与实际情况不一致）
     */
    @Transactional
    void allocateDevice(String requestNo);

    /**
     * 发起采购
     * <p>
     * DAG 引擎节点调用。为无库存的设备申领自动生成采购申请单，
     * 采购单状态为"待审批"（1），并将采购单号关联到申领记录。
     * </p>
     *
     * @param requestNo 申领单号，不可为空
     * @return 生成的采购单号
     */
    @Transactional
    String createPurchaseOrder(String requestNo);

    /**
     * 采购到货处理
     * <p>
     * 管理端调用。当采购的设备到货后，更新采购单状态为"已到货"（4），
     * 并触发 DAG 引擎继续执行后续流程（如分配设备）。
     * </p>
     *
     * @param orderNo 采购单号，不可为空
     */
    @Transactional
    void onPurchaseArrived(String orderNo);

    /**
     * 完成设备申领
     * <p>
     * 设备分配完成后，将申领记录状态置为"已完成"（5），标记流程结束。
     * </p>
     *
     * @param requestNo 申领单号，不可为空
     */
    @Transactional
    void completeRequest(String requestNo);

    /**
     * 根据申领单号查询申领记录
     *
     * @param requestNo 申领单号
     * @return 申领记录实体，若不存在则返回 null
     */
    AdminDeviceRequest getByRequestNo(String requestNo);

    /** 管理员确认归还入库：设备重新入库 + 库存回补 + 归还记录 + 申领单更新 */
    void confirmReturn(Long returnId, String remark);
}