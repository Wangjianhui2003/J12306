package com.jianhui.project.framework.starter.bases.config;

import com.jianhui.project.framework.starter.bases.safe.FastJsonSafeMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 基础自动配置
 */
public class ApplicationBaseAutoConfiguration {

    /**
     * 默认不开启安全模式
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "framework.fastjson.safe-mode", havingValue = "true")
    public FastJsonSafeMode goFastJsonSafeMode(){
        return new FastJsonSafeMode();
    }
}
