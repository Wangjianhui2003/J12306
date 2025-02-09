package com.jianhui.project.framework.starter.bases.safe;

import org.springframework.beans.factory.InitializingBean;

/**
 * fastjson安全模式,开启后关闭类型隐式转换
 */
public class FastJsonSafeMode implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        System.setProperty("fastjson2.parse.safeMode","true");
    }
}
