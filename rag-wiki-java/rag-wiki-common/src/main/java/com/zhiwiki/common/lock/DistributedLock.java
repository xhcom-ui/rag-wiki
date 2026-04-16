package com.zhiwiki.common.lock;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解
 * 基于Redisson实现，防止并发重复操作
 * 
 * 使用示例:
 * <pre>
 * {@code @DistributedLock(key = "'doc:' + #documentId")}
 * public void processDocument(String documentId) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁的key，支持SpEL表达式
     * 例如: "'user:lock:' + #userId"
     */
    String key();

    /**
     * key前缀
     */
    String prefix() default "lock:";

    /**
     * 等待获取锁的最长时间
     */
    long waitTime() default 3;

    /**
     * 持锁最长时间（自动释放）
     */
    long leaseTime() default 10;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 获取锁失败时的提示信息
     */
    String failMessage() default "操作过于频繁，请稍后重试";
}
