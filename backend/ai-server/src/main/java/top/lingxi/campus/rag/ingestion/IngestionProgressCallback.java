package top.lingxi.campus.rag.ingestion;

/**
 * 入库进度回调
 *
 * 修复点：原实现只有 0% / 100% 两个进度点（前端进度条假进度）。
 * 现在按 embedding/入库批次上报真实进度。
 */
@FunctionalInterface
public interface IngestionProgressCallback {

    IngestionProgressCallback NOOP = (completed, total) -> {
    };

    /**
     * @param completed 已完成入库的分片数
     * @param total     总分片数
     */
    void onProgress(int completed, int total);
}