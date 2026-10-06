package top.lingxi.campus.common.constant;


/**
 * 缓存常量定义
 */
public class CacheConstants {

    private CacheConstants() {
    }

    /** 分组列表缓存键前缀 */
    public static final String GROUP_LIST_KEY_PREFIX = "cache:groups:userId:";


    /** 分组列表本地缓存名 */
    public static final String CAFFEINE_GROUP_LIST = "groups";


    // ==================== TTL 配置 ====================

    /** 缓存基础 TTL（小时） */
    public static final long BASE_TTL_HOURS = 1;


    /** 获取分组列表缓存键 */
    public static String getGroupListKey(Long userId) {
        return GROUP_LIST_KEY_PREFIX + userId;
    }

}