package top.lingxi.campus.itAgent.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lingxi.campus.itAgent.context.ChatContext;
import top.lingxi.campus.infra.config.WanxProperties;
import top.lingxi.campus.ai.service.IWanxImageService;

import top.lingxi.campus.result.WanxImageResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageGenerationHandler implements IntentHandler {

    private final IWanxImageService wanxImageService;
    private final WanxProperties wanxProperties;

    @Override
    public List<String> getDomains() {
        return List.of("IT");
    }

    @Override
    public int getOrder() {
        return 3;
    }

    @Override
    public boolean supports(ChatContext ctx) {
        String intent = ctx.getIntent() != null ? ctx.getIntent().getIntent() : null;
        return "image_generation".equals(intent);
    }

    @Override
    public Flux<String> handle(ChatContext ctx) {
        Long sessionId = ctx.getFinalSessionId();
        String imagePrompt = ctx.getIntent().getImagePrompt();

        try {
            WanxImageResult imageResult = wanxImageService.generateImage(imagePrompt, sessionId);
            String ossUrl = imageResult.getImageUrl();

            // 元数据供 Pipeline 保存
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("model", wanxProperties.getModel());
            metadata.put("prompt", imagePrompt);
            metadata.put("image_url", ossUrl);
            metadata.put("original_url", imageResult.getOriginalUrl());
            ctx.setResponseMetadata(metadata);

            // 设置要保存的完整内容
            ctx.setResponseContent("![image](" + ossUrl + ")");

            String promptEcho = "为您生成图片: " + imagePrompt;
            return Flux.just(
                    "AI_PROMPT:" + promptEcho,
                    "IMAGE_URL:" + ossUrl,
                    "DONE"
            );

        } catch (Exception e) {
            log.error("Image generation failed: {}", e.getMessage());
            String error = "ERROR:图片生成失败，请稍后重试";
            ctx.setResponseContent(error);
            return Flux.just(error);
        }
    }
}