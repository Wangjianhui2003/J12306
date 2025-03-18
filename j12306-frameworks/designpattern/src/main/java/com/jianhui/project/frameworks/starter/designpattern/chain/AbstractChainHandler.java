package com.jianhui.project.frameworks.starter.designpattern.chain;

import org.springframework.core.Ordered;

/**
 * 抽象责任链组件
 */
public interface AbstractChainHandler<T> extends Ordered {

    /**
     * 责任链处理逻辑
     *
     * @param requestParam 责任链执行入参
     */
    void handler(T requestParam);

    /**
     * @return 责任链组件标识
     */
    String mark();
}
