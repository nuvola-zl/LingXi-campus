package top.lingxi.campus.rag.ingestion;

/**
 * 入库异常
 *
 * 修复点：原 HybridVectorStore 在 embedding 失败时静默返回空列表，
 * 导致 media 被标记为 PARSED 但库里什么都没有（且不走重试/DLQ）。
 * 现在统一抛 IngestionException，由消费侧的 MQ 重试与死信机制接住。
 */
public class IngestionException extends RuntimeException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}