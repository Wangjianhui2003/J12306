package com.jianhui.project.framework.starter.convention.exception;

import com.google.common.base.Strings;
import com.jianhui.project.framework.starter.convention.errorcode.IErrorCode;
import lombok.Getter;

import java.util.Optional;

/**
 * 异常基类,客户端异常,服务端异常,远程服务异常
 */
@Getter
public abstract class AbstractException extends RuntimeException {

    public final String errorCode;

    public final String errorMessage;

    public AbstractException(IErrorCode errorCode, String errorMessage, Throwable throwable) {
        super(errorMessage, throwable);
        this.errorCode = errorCode.code();
//        message为null或空时,使用errorCode的message
        this.errorMessage = Optional.ofNullable(Strings.emptyToNull(errorMessage)).orElse(errorCode.message());
    }
}
