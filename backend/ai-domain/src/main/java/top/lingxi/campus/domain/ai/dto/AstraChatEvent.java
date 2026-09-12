package top.lingxi.campus.domain.ai.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Astra 对话 SSE 事件模型
 *
 * 重构说明：原实现在 Service 层手拼 "event: xxx\ndata: yyy\n\n" 字符串，
 * Controller 再反向解析还原事件（拼→拆→拼，双重维护）。
 * 本类将事件建模为结构化对象，SSE 序列化收敛到唯一出口，
 * 传输层格式（事件名 / data 内容）与原实现逐字节兼容。
 *
 * data 字段为 String 的原因：answer 事件的 data 是 LLM 输出的原始文本
 * （按 text/event-stream 规范原样透传，不能被 JSON 加引号），
 * 而 session-created / complete 事件的 data 是 JSON 对象。
 * 因此序列化在工厂方法内完成，保证线上协议不变。
 */
public class AstraChatEvent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String EVENT_SESSION_CREATED = "session-created";
    public static final String EVENT_THINKING = "thinking";
    public static final String EVENT_ANSWER = "answer";
    public static final String EVENT_COMPLETE = "complete";
    public static final String EVENT_ERROR = "error";

    private final String event;
    private final String data;

    private AstraChatEvent(String event, String data) {
        this.event = event;
        this.data = data;
    }

    public static AstraChatEvent sessionCreated(Long sessionId) {
        return new AstraChatEvent(EVENT_SESSION_CREATED, writeJson(Map.of("sessionId", sessionId)));
    }

    public static AstraChatEvent thinking(String text) {
        return new AstraChatEvent(EVENT_THINKING, text);
    }

    public static AstraChatEvent answer(String chunk) {
        return new AstraChatEvent(EVENT_ANSWER, chunk);
    }

    /**
     * 完成事件，携带真实消息 ID 与引用来源列表（答案溯源）
     */
    public static AstraChatEvent complete(Long messageId, List<String> citations) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", messageId);
        payload.put("citations", citations != null ? citations : List.of());
        return new AstraChatEvent(EVENT_COMPLETE, writeJson(payload));
    }

    public static AstraChatEvent error(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message != null ? message : "unknown error");
        return new AstraChatEvent(EVENT_ERROR, writeJson(payload));
    }

    private static String writeJson(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SSE 事件序列化失败: " + payload, e);
        }
    }

    public String getEvent() {
        return event;
    }

    public String getData() {
        return data;
    }
}