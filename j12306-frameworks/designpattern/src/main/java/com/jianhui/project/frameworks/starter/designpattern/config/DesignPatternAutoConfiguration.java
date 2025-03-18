package com.jianhui.project.frameworks.starter.designpattern.config;

import com.jianhui.project.framework.starter.bases.config.ApplicationBaseAutoConfiguration;
import com.jianhui.project.frameworks.starter.designpattern.chain.AbstractChainContext;
import com.jianhui.project.frameworks.starter.designpattern.strategy.AbstractStrategyChoose;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 设计模式自动装配
 */
@ImportAutoConfiguration(ApplicationBaseAutoConfiguration.class)
public class DesignPatternAutoConfiguration {

    /**
     * 责任链上下文
     */
    @Bean
    public AbstractChainContext abstractChainContext() {
        return new AbstractChainContext();
    }

    /**
     * 策略模式选择器
     */
    @Bean
    public AbstractStrategyChoose abstractStrategyChoose(){
        return new AbstractStrategyChoose();
    }
}
