package com.jianhui.project.framework.starter.common.enums;

public enum DelEnum {

    /**
     * 正常
     */
    NORMAL(0),

    /**
     * 删除
     */
    DELETED(1);

    private final Integer statusCode;

    DelEnum(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Integer code() {
        return this.statusCode;
    }

    public String strCode() {
        return String.valueOf(this.statusCode);
    }

    @Override
    public String toString() {
        return strCode();
    }
}
