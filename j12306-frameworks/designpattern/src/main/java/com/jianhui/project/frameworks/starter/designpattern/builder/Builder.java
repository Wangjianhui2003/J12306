package com.jianhui.project.frameworks.starter.designpattern.builder;

import java.io.Serializable;

/**
 * 建造者模式接口
 */
public interface Builder<T> extends Serializable {

    /**
     * 构建方法
     * @return 构建对象
     */
    T build();


}
