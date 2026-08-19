package com.minimall.ai.memory;

/**
 * 当前请求的用户上下文 (ThreadLocal)。
 * <p>
 * 为什么需要它: LangChain4j 的 @Tool 方法拿不到 @MemoryId(userId), 而“记住用户事实”这个工具
 * 又必须知道是记给谁。AiServices 的一次 chat() 调用 + 其内部触发的工具执行, 都在同一个线程里同步完成,
 * 所以在 Controller/Service 层把 userId 放进 ThreadLocal, 工具里就能取到, 调用结束再清理。
 */
public final class UserContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    /** 进入一次对话前设置当前 userId */
    public static void set(String userId) {
        CURRENT.set(userId);
    }

    /** 工具里取当前 userId (可能为 null) */
    public static String get() {
        return CURRENT.get();
    }

    /** 对话结束必须清理, 否则线程池复用时会把 userId 串给下一个请求 */
    public static void clear() {
        CURRENT.remove();
    }
}
