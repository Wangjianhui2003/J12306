package com.jianhui.project.framework.starter.bases;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 单例对象容器
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Singleton {

    private static final ConcurrentHashMap<String,Object> SINGLE_OBJECT_POOL = new ConcurrentHashMap<>();

//    TODO:怎么推断?
    public static <T> T get(String key){
        Object result = SINGLE_OBJECT_POOL.get(key);
        return result == null ? null : (T) result;
    }

    /**
     * 通过key或supplier获得单例对象
     */
    public static <T> T get(String key, Supplier<T> supplier){
        Object result = SINGLE_OBJECT_POOL.get(key);
        if (result == null && (result = supplier.get()) != null){
            SINGLE_OBJECT_POOL.put(key,result);
        }
        return result == null ? null : (T) result;
    }

    public static void put(Object value){
        SINGLE_OBJECT_POOL.put(value.getClass().getName(),value);
    }

    public static void put(String key, Object value){
        SINGLE_OBJECT_POOL.put(key,value);
    }
}
