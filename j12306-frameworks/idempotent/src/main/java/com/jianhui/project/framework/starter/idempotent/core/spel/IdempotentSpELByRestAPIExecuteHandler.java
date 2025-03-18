package com.jianhui.project.framework.starter.idempotent.core.spel;

import com.jianhui.project.framework.starter.convention.exception.ClientException;
import com.jianhui.project.framework.starter.idempotent.core.AbstractIdempotentExecuteHandler;
import com.jianhui.project.framework.starter.idempotent.core.IdempotentAspect;
import com.jianhui.project.framework.starter.idempotent.core.IdempotentContext;
import com.jianhui.project.framework.starter.idempotent.core.IdempotentParamWrapper;
import com.jianhui.project.framework.starter.idempotent.annotation.Idempotent;
import com.jianhui.project.framework.starter.idempotent.toolkit.SpELUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * 基于 SpEL 方法验证请求幂等性，适用于 MQ 场景
 */
@RequiredArgsConstructor
public final class IdempotentSpELByRestAPIExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentSpELService {

    private final RedissonClient redissonClient;

    private final static String LOCK = "lock:spEL:restAPI";

    @SneakyThrows
    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        Idempotent idempotent = IdempotentAspect.getIdempotent(joinPoint);
//        TODO:在干什么?
        String key = (String) SpELUtil.parseKey(idempotent.key(), ((MethodSignature) joinPoint.getSignature()).getMethod(), joinPoint.getArgs());
        return IdempotentParamWrapper.builder().lockKey(key).joinPoint(joinPoint).build();

    }

    @Override
    public void handler(IdempotentParamWrapper wrapper) {
        String uniqueKey = wrapper.getIdempotent().uniqueKeyPrefix() + wrapper.getLockKey();
        RLock rLock = redissonClient.getLock(uniqueKey);
        if(!rLock.tryLock()){
            throw new ClientException(wrapper.getIdempotent().message());
        }
        IdempotentContext.put(LOCK,rLock);
    }

    @Override
    public void postProcessing() {
        RLock rLock = null;
        try {
            rLock = (RLock)IdempotentContext.getKey(LOCK);
        } finally {
            if(rLock != null){
                rLock.unlock();
            }
        }
    }

    @Override
    public void exceptionProcessing() {
        postProcessing();
    }
}
