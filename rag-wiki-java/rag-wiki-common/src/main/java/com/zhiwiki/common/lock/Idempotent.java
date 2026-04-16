package com.zhiwiki.common.lock;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 接口幂等性注解
 * 基于Redis实现，防止表单重复提交
 * 
 * 使用示例:
 * <pre>
 * {@code @Idempotent(key = "'submit:' + #userId", expireTime = 5)}
 * public Result<?> submitApproval(String userId, ...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等key，支持SpEL表达式
     * 不填则默认使用 类名:方法名:参数hash
     */
    String key() default "";

    /**
     * key前缀
     */
    String prefix() default "idempotent:";

    /**
     * 幂等有效期
     */
    long expireTime() default 5;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 重复提交时的提示
     */
    String message() default "请勿重复提交";
}
