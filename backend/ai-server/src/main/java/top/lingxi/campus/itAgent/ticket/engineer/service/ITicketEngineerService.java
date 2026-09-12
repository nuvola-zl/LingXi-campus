package top.lingxi.campus.itAgent.ticket.engineer.service;



import top.lingxi.campus.domain.engineer.vo.EngineerTicketVO;

import java.util.List;

public interface ITicketEngineerService {

    /**
     * 获取工程师的待处理工单列表
     * 包括：分配到自己所在组的未接单工单 + 已分配给自己的工单
     */
    List<EngineerTicketVO> getPendingList(Long engineerUserId);

    /**
     * 获取工程师的历史工单（已关闭）
     */
    List<EngineerTicketVO> getHistoryList(Long engineerUserId, Integer page, Integer pageSize);

    /**
     * 工单详情（含处理时间线）
     */
    EngineerTicketVO getDetail(Long ticketId);

    /**
     * 接单
     */
    void claim(Long ticketId, Long engineerUserId);

    /**
     * 添加处理记录
     */
    void addComment(Long ticketId, Long engineerUserId, String content);

    /**
     * 标记解决（等待用户确认）
     */
    void resolve(Long ticketId, Long engineerUserId);
}