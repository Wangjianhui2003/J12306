package com.jianhui.project.framework.starter.convention.exception;

import com.jianhui.project.framework.starter.convention.errorcode.IErrorCode;

public class RemoteException extends AbstractException {

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
