package com.jianhui.project.gatewayservice.config;

import lombok.Data;

import java.util.List;

/**
 * 过滤器配置
 */
@Data
public class Config {

    /**
     * 黑名单前置路径
     */
    private List<String> blackPathPrefix;

}
