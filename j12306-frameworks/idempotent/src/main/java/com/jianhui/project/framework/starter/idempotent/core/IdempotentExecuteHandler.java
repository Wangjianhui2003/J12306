package com.jianhui.project.framework.starter.idempotent.core;

import com.jianhui.project.framework.starter.idempotent.annotation.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 *  幂等执行处理器
 */
public interface IdempotentExecuteHandler {

    /**
     * 幂等处理逻辑
     * @param wrapper the wrapper
     */
    void handler(IdempotentParamWrapper wrapper);

    /**
     * 执行幂等处理逻辑
     *
     * @param joinPoint  the join point
     * @param idempotent 幂等注解
     */
    void execute(ProceedingJoinPoint joinPoint, Idempotent idempotent);

    /**
     * 异常处理流程
     */
    default void exceptionProcessing() {

    }

    /**
     * 后置处理流程
     */
    default void postProcessing(){

    }
}
