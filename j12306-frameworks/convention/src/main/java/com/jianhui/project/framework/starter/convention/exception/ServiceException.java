package com.jianhui.project.framework.starter.convention.exception;

import com.jianhui.project.framework.starter.convention.errorcode.IErrorCode;

/**
 * 服务端异常
 */
public class ServiceException extends AbstractException {

    public ServiceException(String message) {
        this(null, message, null);
    }

    public ServiceException(IErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public ServiceException(IErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ServiceException(IErrorCode errorCode, String errorMessage, Throwable throwable) {
        super(errorCode, errorMessage, throwable);
    }

    @Override
    public String toString() {
        return "ServiceException{" +
                "errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
