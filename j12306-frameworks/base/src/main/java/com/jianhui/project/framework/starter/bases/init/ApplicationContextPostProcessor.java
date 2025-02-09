package com.jianhui.project.framework.starter.bases.init;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;

/**
 * 应用初始化后的后置处理器,防止spring事件多次执行 TODO:为什么是ReadyEvent
 */
@RequiredArgsConstructor
public class ApplicationContextPostProcessor implements ApplicationListener<ApplicationReadyEvent> {

    private final ApplicationContext applicationContext;

    /**
     * 是否只执行一次
     */
    private boolean executeOnlyOnce = true;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        synchronized (ApplicationContextPostProcessor.class) {
            if (executeOnlyOnce) {
//                应用准备好后发布应用初始化事件
                applicationContext.publishEvent(new ApplicationInitializingEvent(this));
                executeOnlyOnce = false;
            }
        }
    }
}
