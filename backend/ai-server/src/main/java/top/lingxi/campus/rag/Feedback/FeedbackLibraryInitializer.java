package top.lingxi.campus.rag.Feedback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.lingxi.campus.domain.ai.entity.KbLibrary;
import top.lingxi.campus.domain.ai.mapper.KbLibraryMapper;
import top.lingxi.campus.infra.config.FeedbackProperties;

import java.util.Map;

/**
 * 反馈知识库初始化器
 *
 * Phase 3 修复：原实现无任何开关，应用每次启动都会向 kb_library
 * 写入三条反馈库记录（即使反馈功能整体关闭）。
 * 现增加 FeedbackProperties 总开关：feedback.enabled=false 时
 * 完全不触碰数据库，反馈流程对系统零影响（保留代码仅作演示）。
 *
 * TODO(业务下线后清理): 反馈知识沉淀功能确认废弃后，
 *  可整体删除本类 + TicketKnowledgeFeedbackService + FeedbackProperties
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackLibraryInitializer {

    private final KbLibraryMapper libraryMapper;
    private final FeedbackProperties feedbackProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // 反馈功能总开关：关闭时不创建任何反馈库
        if (!feedbackProperties.isEnabled()) {
            log.info("反馈知识沉淀功能已关闭，跳过反馈库初始化");
            return;
        }

        Map<String, String> domainMap = Map.of(
                "IT", "IT_feedback",
                "HR", "HR_feedback",
                "行政", "行政_feedback"
        );

        for (Map.Entry<String, String> entry : domainMap.entrySet()) {
            String libName = entry.getValue();
            if (libraryMapper.selectByName(libName) == null) {
                KbLibrary lib = new KbLibrary();
                lib.setName(libName);
                lib.setDescription(entry.getKey() + " 工单自动沉淀库（反馈知识，非官方SOP）");
                lib.setType("personal");
                lib.setOwnerId(1L);   // 系统管理员，建议配成配置项
                lib.setIsTop(false);
                libraryMapper.insert(lib);
                log.info("自动创建反馈知识库: {}", libName);
            }
        }
    }
}