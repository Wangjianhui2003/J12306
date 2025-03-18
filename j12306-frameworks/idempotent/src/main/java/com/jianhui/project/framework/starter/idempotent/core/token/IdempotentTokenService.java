package com.jianhui.project.framework.starter.idempotent.core.token;

import com.jianhui.project.framework.starter.idempotent.core.IdempotentExecuteHandler;

/**
 * Token实习幂等
 */
public interface IdempotentTokenService extends IdempotentExecuteHandler {

    /**
     * 创建幂等验证Token
     * @return token
     */
    String createToken();
}
