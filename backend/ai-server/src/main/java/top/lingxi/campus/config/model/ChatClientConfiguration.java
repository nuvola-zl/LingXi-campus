package top.lingxi.campus.config.model;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.lingxi.campus.adviser.ReasoningContentAdvisor;
import top.lingxi.campus.common.constant.PromptConstant;

import top.lingxi.campus.tool.admin.DeviceTools;
import top.lingxi.campus.tool.hr.HrTools;
import top.lingxi.campus.tool.hr.MeetingRoomTools;

@Configuration
public class ChatClientConfiguration {

    @Bean
    public ChatClient chatClient(DashScopeChatModel model) {
        return ChatClient.builder(model)
                .defaultAdvisors(new ReasoningContentAdvisor(0))
                .build();
    }


    /**
     * 用于Astra知识库对话
     * 1. maxToken 从全局默认 2000 提升为 4096：RAG 带长上下文时，
     *    2000 的回答上限会导致答案被截断
     * 2. system prompt 收敛为身份与底线设定；具体的回答要求（引用来源、拒答、
     *    结构等）由 AstraSearchServiceImpl.buildPrompt 在用户消息中给出，
     *    避免 system 与 user 双重指令互相覆盖
     */
    @Bean
    public ChatClient astraClient(DashScopeChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(PromptConstant.ASTRA_SYSTEM_PROMPT)
                .defaultOptions(DashScopeChatOptions.builder()
                        .maxToken(4096)
                        .build())
                .build();
    }


    @Bean
    public ChatClient hrChatClient(DashScopeChatModel model,
                                   HrTools hrTools) {
        return ChatClient.builder(model)
//                .defaultSystem(PromptConstant.HR_SYSTEM_PROMPT)
                .defaultTools(hrTools)
                .defaultAdvisors(new ReasoningContentAdvisor(0))
                .build();
    }


    @Bean
    public ChatClient adminChatClient(DashScopeChatModel model,
                                      MeetingRoomTools meetingRoomTools,
                                      DeviceTools deviceTools) {
        return ChatClient.builder(model)
//                .defaultSystem(PromptConstant.ADMIN_SYSTEM_PROMPT)
                .defaultTools(meetingRoomTools, deviceTools)
                .defaultAdvisors(new ReasoningContentAdvisor(0))
                .build();
    }
}