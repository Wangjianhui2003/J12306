package com.jianhui.project.framework.starter.idempotent.core.spel;

import com.jianhui.project.framework.starter.cache.DistributedCache;
import com.jianhui.project.framework.starter.idempotent.core.AbstractIdempotentExecuteHandler;
import com.jianhui.project.framework.starter.idempotent.core.IdempotentParamWrapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 基于 SpEL 方法验证请求幂等性，适用于 MQ 场景
 */
@RequiredArgsConstructor
public final class IdempotentSpELByMQExecuteHandler extends AbstractIdempotentExecuteHandler {

    private final static int TIMEOUT = 600;
    private final static String WRAPPER = "wrapper:spEL:MQ";
    private final static String LUA_SCRIPT_SET_IF_ABSENT_AND_GET_PATH = "lua/set_if_absent_and_get.lua";
    private final DistributedCache distributedCache;

    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        return null;
    }

    @Override
    public void handler(IdempotentParamWrapper wrapper) {

    }

    @Override
    public void exceptionProcessing() {
        super.exceptionProcessing();
    }

    @Override
    public void postProcessing() {
        super.postProcessing();
    }
}
