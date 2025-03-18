package com.jianhui.project.framework.starter.idempotent.enums;

/**
 * 幂等验证方式类型枚举
 *
 * @author wjh2
 * @date 2025/02/28
 */
public enum IdempotentTypeEnum {

    /**
     * 基于Token方式验证
     */
    TOKEN,

    /**
     * 基于方法参数方式验证
     */
    PARAM,

    /**
     * SpEL表达式方式验证
     */
    SPEL
}
