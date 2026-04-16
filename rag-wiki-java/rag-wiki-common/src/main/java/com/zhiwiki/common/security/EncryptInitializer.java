package com.zhiwiki.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 加密组件初始化器
 * 
 * 在Spring Boot启动后，将DataEncryptor实例注入到EncryptTypeHandler
 * 解决MyBatis TypeHandler无法使用Spring依赖注入的问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncryptInitializer implements CommandLineRunner {

    private final DataEncryptor dataEncryptor;

    @Override
    public void run(String... args) {
        EncryptTypeHandler.setDataEncryptor(dataEncryptor);
        log.info("字段加密TypeHandler初始化完成，DataEncryptor已注入");
    }
}
