package com.zhiwiki.common.lock;

import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 幂等性AOP切面
 * 通过Redis SETNX实现接口防重复提交
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String idempotentKey = buildKey(idempotent, joinPoint);

        // 尝试设置幂等标记
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                idempotentKey, "1",
                idempotent.expireTime(),
                idempotent.timeUnit()
        );

        if (Boolean.FALSE.equals(success)) {
            log.warn("重复提交拦截: key={}", idempotentKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS.getCode(), idempotent.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 业务异常时删除幂等标记，允许重试
            stringRedisTemplate.delete(idempotentKey);
            throw e;
        }
    }

    private String buildKey(Idempotent idempotent, ProceedingJoinPoint joinPoint) {
        String key = idempotent.key();
        if (key != null && !key.isEmpty()) {
            return idempotent.prefix() + parseSpEL(key, joinPoint);
        }

        // 默认key: 类名:方法名:参数hash
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        int argsHash = Arrays.deepHashCode(joinPoint.getArgs());

        return idempotent.prefix() + className + ":" + methodName + ":" + argsHash;
    }

    private String parseSpEL(String expression, ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
            if (paramNames == null) return expression;

            EvaluationContext context = new StandardEvaluationContext();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
            return PARSER.parseExpression(expression).getValue(context, String.class);
        } catch (Exception e) {
            return expression;
        }
    }
}
