package com.zhiwiki.common.lock;

import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 分布式锁AOP切面
 * 拦截@DistributedLock注解，自动获取/释放Redisson锁
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RedissonClient.class)
public class DistributedLockAspect {

    private final RedissonClient redissonClient;

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        // 解析锁key（支持SpEL）
        String lockKey = distributedLock.prefix() + parseSpEL(distributedLock.key(), joinPoint);

        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!acquired) {
                log.warn("获取分布式锁失败: key={}", lockKey);
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS.getCode(), distributedLock.failMessage());
            }

            log.debug("获取分布式锁成功: key={}", lockKey);
            return joinPoint.proceed();

        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁: key={}", lockKey);
            }
        }
    }

    /**
     * 解析SpEL表达式
     */
    private String parseSpEL(String expression, ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] paramNames = NAME_DISCOVERER.getParameterNames(method);

            if (paramNames == null || paramNames.length == 0) {
                return expression;
            }

            EvaluationContext context = new StandardEvaluationContext();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }

            return PARSER.parseExpression(expression).getValue(context, String.class);
        } catch (Exception e) {
            log.warn("SpEL解析失败，使用原始key: expression={}, error={}", expression, e.getMessage());
            return expression;
        }
    }
}
