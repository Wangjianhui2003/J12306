package com.jianhui.project.framework.starter.convention.exception;

import com.jianhui.project.framework.starter.convention.errorcode.BaseErrorCode;
import com.jianhui.project.framework.starter.convention.errorcode.IErrorCode;

/**
 * 客户端异常
 */
public class ClientException extends AbstractException {

    /**
     * @param errorCode 错误码
     */
    public ClientException(IErrorCode errorCode) {
        this(errorCode,null, null);
    }

    /**
     * 使用默认客户端错误码
     * @param errorMessage 异常信息
     */
    public ClientException(String errorMessage) {
        this(BaseErrorCode.CLIENT_ERROR, errorMessage, null);
    }

    /**
     * @param errorCode 错误码
     * @param errorMessage 异常信息
     */
    public ClientException(IErrorCode errorCode, String errorMessage) {
        this(errorCode, errorMessage, null);
    }

    /**
     * @param errorCode 错误码
     * @param errorMessage 异常信息
     * @param throwable 异常
     */
    public ClientException(IErrorCode errorCode, String errorMessage, Throwable throwable) {
        super(errorCode, errorMessage, throwable);
    }

    @Override
    public String toString() {
        return "ClientException{" +
                "errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
