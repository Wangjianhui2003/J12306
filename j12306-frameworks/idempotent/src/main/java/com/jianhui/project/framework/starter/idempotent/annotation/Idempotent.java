package com.jianhui.project.framework.starter.idempotent.annotation;

import com.jianhui.project.framework.starter.idempotent.enums.IdempotentSceneEnum;
import com.jianhui.project.framework.starter.idempotent.enums.IdempotentTypeEnum;

import java.lang.annotation.*;

/**
 * 幂等注解
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等key
     * @return {@link String }
     */
    String key() default "";

    /**
     * 触发幂等失败逻辑的消息
     * @return {@link String }
     */
    String message() default "您操作太快，请稍后再试";

    /**
     * 幂等场景 枚举
     * @return {@link IdempotentSceneEnum }
     */
    IdempotentSceneEnum scene() default IdempotentSceneEnum.RESTAPI;

    /**
     * 验证幂等类型 枚举 默认为方法参数验证
     * @return {@link IdempotentTypeEnum }
     */
    IdempotentTypeEnum type() default IdempotentTypeEnum.PARAM;

    /**
     * 设置防重令牌 Key 前缀，MQ 幂等去重可选设置
     * @return {@link String }
     */
    String uniqueKeyPrefix() default "";

    /**
     * 设置防重令牌 Key 过期时间，单位秒，默认 1 小时，MQ 幂等去重可选设置
     * {@link IdempotentSceneEnum#MQ} and {@link IdempotentTypeEnum#SPEL} 时生效
     */
    long keyTimeOut() default 3600L;
}
