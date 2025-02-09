package com.jianhui.project.framework.starter.bases.init;


import org.springframework.context.ApplicationEvent;

/**
 * 应用初始化事件
 */
public class ApplicationInitializingEvent extends ApplicationEvent {


    public ApplicationInitializingEvent(Object source) {
        super(source);
    }
}
