package com.jianhui.project.framework.starter.idempotent.core;

import com.jianhui.project.framework.starter.idempotent.annotation.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * 幂等切面
 */
@Aspect
public class IdempotentAspect {

    @Around("@annotation(com.jianhui.project.framework.starter.idempotent.annotation.Idempotent)")
    public Object idempotentHandler(ProceedingJoinPoint joinPoint) throws Throwable {
        Idempotent idempotent = getIdempotent(joinPoint);
//        根据注解的场景和类型获取对应的幂等执行处理器
        IdempotentExecuteHandler instance = IdempotentExecuteHandlerFactory.getInstance(idempotent.scene(), idempotent.type());
        Object result;
        try {
//            处理幂等
            instance.execute(joinPoint, idempotent);
            result = joinPoint.proceed();
//            后置处理
            instance.postProcessing();
        } catch (RepeatConsumptionException ex) {
            /**
             * 触发幂等逻辑时可能有两种情况：
             *    * 1. 消息还在处理，但是不确定是否执行成功，那么需要返回错误，方便 RocketMQ 再次通过重试队列投递
             *    * 2. 消息处理成功了，该消息直接返回成功即可
             */
            if (!ex.getError()) {
                return null;
            }
            throw ex;
        } catch (Throwable ex) {
            // 客户端消费存在异常，需要删除幂等标识方便下次 RocketMQ 再次通过重试队列投递
            instance.exceptionProcessing();
            throw ex;
        } finally {
            IdempotentContext.clean();
        }
        return result;
    }


    /**
     * 从切点获得Idempotent注解
     * @param joinPoint
     * @return
     * @throws NoSuchMethodException
     */
    public static Idempotent getIdempotent(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method declaredMethod = joinPoint.getTarget().getClass()
                .getDeclaredMethod(signature.getName(), signature.getMethod().getParameterTypes());
        return declaredMethod.getAnnotation(Idempotent.class);
    }

}
