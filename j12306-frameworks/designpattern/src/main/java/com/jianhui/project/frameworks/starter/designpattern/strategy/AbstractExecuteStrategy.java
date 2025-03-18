package com.jianhui.project.frameworks.starter.designpattern.strategy;

/**
 * 策略模式抽象接口
 */
public interface AbstractExecuteStrategy<REQUEST,RESPONSE> {

    /**
     * 策略标识,用于排除重复的策略
     */
    default String mark() {
        return null;
    }

    /**
     * 策略匹配标识
     */
    default String patternMatchMark() {
        return null;
    }

    /**
     * 执行策略
     * @param requestParam 执行策略入参
     */
    default void execute(REQUEST requestParam) {

    }

    /**
     * 执行策略，带返回值
     * @param requestParam 执行策略入参
     * @return 执行策略后返回值
     */
    default RESPONSE executeResp(REQUEST requestParam) {
        return null;
    }
}
