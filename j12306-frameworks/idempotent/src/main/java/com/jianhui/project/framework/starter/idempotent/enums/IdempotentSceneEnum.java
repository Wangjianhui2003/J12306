package com.jianhui.project.framework.starter.idempotent.enums;

/**
 * 幂等场景枚举
 */
public enum IdempotentSceneEnum {

    /**
     * 消息队列场景
     */
    MQ,

    /**
     * REST API 场景
     */
    RESTAPI
}
