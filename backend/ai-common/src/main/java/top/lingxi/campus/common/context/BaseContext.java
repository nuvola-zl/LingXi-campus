package top.lingxi.campus.common.context;


public class BaseContext {
    private static final ThreadLocal<Long> userId = new ThreadLocal<>();
    private static final ThreadLocal<String> roleType = new ThreadLocal<>();

    public static void setCurrentUser(Long uid, String role) {
        userId.set(uid);
        roleType.set(role);
    }

    public static Long getCurrentId() {
        return userId.get();
    }

    public static String getCurrentRoleType() {
        return roleType.get();
    }

    public static void removeCurrentUser() {
        userId.remove();
        roleType.remove();
    }
}
