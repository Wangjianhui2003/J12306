package com.jianhui.project.framework.starter.database.handler;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jianhui.project.framework.starter.distributedid.toolkit.SnowflakeIdUtil;

/**
 * 自定义ID生成器
 */
public class CustomIdGenerator implements IdentifierGenerator {
    @Override
    public Number nextId(Object entity) {
        return SnowflakeIdUtil.nextId();
    }
}
