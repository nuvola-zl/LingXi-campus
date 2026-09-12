package top.lingxi.campus.admin.user.service.impl;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.admin.user.service.PurchaseService;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.domain.admin.eneity.AdminPurchaseRequest;
import top.lingxi.campus.domain.admin.mapper.AdminPurchaseRequestMapper;
import top.lingxi.campus.domain.rocord.eneity.SysApprovalRecord;
import top.lingxi.campus.domain.rocord.mapper.SysApprovalRecordMapper;

import java.time.LocalDateTime;

@Service
public class PurchaseServiceImpl implements PurchaseService {
    
    @Autowired
    private AdminPurchaseRequestMapper purchaseMapper;
    
    @Autowired
    private SysApprovalRecordMapper approvalMapper;
    
    /**
     * 审批采购申请
     */
    @Transactional
    public void approvePurchase(Long purchaseId, boolean approved, String remark) {
        AdminPurchaseRequest purchase = purchaseMapper.selectById(purchaseId);
        if (purchase == null || purchase.getStatus() != 1) {
            throw new RuntimeException("采购单不存在或已处理");
        }
        
        purchase.setStatus(approved ? 2 : 5); // 2已批准 5已取消
        purchase.setApprovedBy(BaseContext.getCurrentId());
        purchase.setApprovedAt(LocalDateTime.now());
        purchaseMapper.updateById(purchase);
        
        // 记录审批日志
        SysApprovalRecord record = new SysApprovalRecord();
        record.setBizType("purchase");
        record.setBizTable("admin_purchase_request");
        record.setBizId(purchaseId);
        record.setAction(approved ? 1 : 2);
        record.setRemark(remark);
        record.setCreatedAt(LocalDateTime.now());
        approvalMapper.insert(record);
    }
}