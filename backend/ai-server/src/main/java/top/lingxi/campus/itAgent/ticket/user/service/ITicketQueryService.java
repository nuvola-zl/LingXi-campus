package top.lingxi.campus.itAgent.ticket.user.service;

import top.lingxi.campus.domain.biz.ticket.entity.BizTicket;

import java.util.List;

/**
 * 工单查询服务
 * 负责把数据库里的工单记录翻译成用户能看懂的人话
 */
public interface ITicketQueryService {

    /**
     * 查询当前用户的工单列表，并组装成自然语言回复
     *
     * @param userId 当前登录用户ID
     * @return 人话格式的工单列表描述
     */
    String buildReply(Long userId);

    /**
     * 根据工单编号查询工单详情，并组装成自然语言回复
     *
     * @param userId 当前登录用户ID
     * @param extractedTicketNo 从用户输入中提取的工单编号
     * @return 人话格式的工单详情描述
     */
    String buildReplyByTicketNo(Long userId, String extractedTicketNo);

    /**
     * 催单：将用户最新未关闭工单优先级设为紧急
     */
    String urgentLatestTicket(Long userId);


    /**
     * 关闭工单（设置已完成的状态）
     */
    String closeLatestTicket(Long userId);

    /**
     * 查询用户工单列表（JSON）
     */
    List<BizTicket> getUserTicketList(Long userId);

    /**
     * 查询用户工单详情含评论（JSON）
     */
    top.lingxi.campus.domain.biz.ticket.entity.BizTicket getUserTicketDetail(Long userId, Long ticketId);

    /**
     * 获取工单评论列表
     */
    List<top.lingxi.campus.domain.biz.ticket.entity.BizTicketComment> getTicketComments(Long ticketId);
}