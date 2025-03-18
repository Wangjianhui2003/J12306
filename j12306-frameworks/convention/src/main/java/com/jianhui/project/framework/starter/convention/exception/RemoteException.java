package com.jianhui.project.framework.starter.convention.exception;

import com.jianhui.project.framework.starter.convention.errorcode.BaseErrorCode;
import com.jianhui.project.framework.starter.convention.errorcode.IErrorCode;

/**
 * 远程服务调用异常
 */
public class RemoteException extends AbstractException {

    public RemoteException(String message) {
        this(BaseErrorCode.REMOTE_ERROR, message, null);
    }

    public RemoteException(IErrorCode errorCode, String errorMessage) {
        this(errorCode, errorMessage, null);
    }

    public RemoteException(IErrorCode errorCode, String errorMessage, Throwable throwable) {
        super(errorCode, errorMessage, throwable);
    }

    @Override
    public String toString() {
        return "RemoteException{" +
                "errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
