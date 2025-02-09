package com.jianhui.project.framework.starter.bases;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * 将上下文容器封装为常量
 */
public class ApplicationContextHolder implements ApplicationContextAware {

    public static ApplicationContext CONTEXT;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        CONTEXT = applicationContext;
    }

    public static <T> T getBean(Class<T> clazz){
        return CONTEXT.getBean(clazz);
    }

    public static <T> T getBean(String name, Class<T> clazz){
        return CONTEXT.getBean(name, clazz);
    }

    /**
     * 获取指定类型的所有bean
     */
    public static <T> Map<String,T> getBeansOfType(Class<T> clazz){
        return CONTEXT.getBeansOfType(clazz);
    }

    /**
     * 获取指定bean的注解
     */
    public static <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType){
        return CONTEXT.findAnnotationOnBean(beanName, annotationType);
    }

    /**
     * 获取容器上下文
     */
    public static ApplicationContext getInstance(){
        return CONTEXT;
    }
}
