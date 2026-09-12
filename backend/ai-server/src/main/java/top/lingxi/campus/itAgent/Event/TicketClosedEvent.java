package top.lingxi.campus.itAgent.Event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 工单关闭事件
 * 触发知识库沉淀
 */
@Getter
public class TicketClosedEvent extends ApplicationEvent {
    
    private final Long ticketId;

    public TicketClosedEvent(Object source, Long ticketId) {
        super(source);
        this.ticketId = ticketId;
    }
}