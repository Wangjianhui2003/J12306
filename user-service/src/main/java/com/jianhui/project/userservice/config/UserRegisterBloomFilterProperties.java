package com.jianhui.project.userservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户注册布隆过滤器配置
 */
@Data
@ConfigurationProperties(prefix = UserRegisterBloomFilterProperties.PREFIX)
public final class UserRegisterBloomFilterProperties {

    /**
     * 配置前缀
     */
    public static final String PREFIX = "framework.cache.redis.bloom-filter.user-register";

    /**
     * 布隆过滤器实例名称
     */
    private String name = "user_register_cache_penetration_bloom_filter";

    /**
     * 期望的每个元素的插入量 TODO:插入量是什么
     */
    private Long expectedInsertions = 64L;

    /**
     * 期望的误判率
     */
    private Double falseProbability = 0.03D;
}
