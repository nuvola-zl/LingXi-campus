package top.lingxi.campus.domain.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.lingxi.campus.domain.ai.entity.ChatMessage;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    /**
     * 根据会话ID获取最近消息列表（按创建时间升序）
     * @param sessionId 会话ID
     * @param limit 限制数量
     * @return 消息列表
     */
    List<ChatMessage> selectBySessionIdOrderByCreatedAt(@Param("sessionId") Long sessionId, @Param("limit") Integer limit);
}
