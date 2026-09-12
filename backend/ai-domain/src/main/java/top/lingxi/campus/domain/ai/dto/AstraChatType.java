package top.lingxi.campus.domain.ai.dto;

/**
 * Astra 对话类型
 *
 * 重构说明：原实现通过 prompt.contains("【用户上传文件") 嗅探上传意图，
 * 脆弱且不可控。改为前端显式声明类型，旧客户端不传 type 时由 Controller
 * 回退到原嗅探逻辑，保持向后兼容。
 */
public enum AstraChatType {

    /**
     * RAG 知识库问答（默认）：检索 → rerank → LLM 生成
     */
    RAG,

    /**
     * 文件直传对话：前端已把文件内容拼进 prompt，直接问 LLM，不走检索
     */
    FILE_UPLOAD
}