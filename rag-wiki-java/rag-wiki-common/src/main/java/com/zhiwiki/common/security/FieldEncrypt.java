package com.zhiwiki.common.security;

import java.lang.annotation.*;

/**
 * 字段加密注解
 * 
 * 标注在实体字段上，MyBatis-Plus TypeHandler 会自动进行加密/解密
 * 
 * 使用示例：
 * <pre>
 * public class User {
 *     &#64;FieldEncrypt
 *     private String phone;      // 写入数据库时自动加密，读取时自动解密
 *     
 *     &#64;FieldEncrypt
 *     private String email;
 * }
 * </pre>
 * 
 * 前提：rag-wiki.encryption.enabled=true 且 rag-wiki.encryption.key 已配置
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FieldEncrypt {
    /**
     * 是否启用加密（可用于开发环境关闭加密）
     */
    boolean enabled() default true;
}
