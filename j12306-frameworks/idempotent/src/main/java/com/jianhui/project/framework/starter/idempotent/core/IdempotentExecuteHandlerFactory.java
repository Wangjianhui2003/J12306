package com.jianhui.project.framework.starter.idempotent.core;

import com.jianhui.project.framework.starter.bases.ApplicationContextHolder;
import com.jianhui.project.framework.starter.idempotent.core.param.IdempotentParamService;
import com.jianhui.project.framework.starter.idempotent.core.spel.IdempotentSpELByMQExecuteHandler;
import com.jianhui.project.framework.starter.idempotent.core.spel.IdempotentSpELByRestAPIExecuteHandler;
import com.jianhui.project.framework.starter.idempotent.core.token.IdempotentTokenService;
import com.jianhui.project.framework.starter.idempotent.enums.IdempotentSceneEnum;
import com.jianhui.project.framework.starter.idempotent.enums.IdempotentTypeEnum;

/**
 * 幂等执行处理器工厂
 * 简单工厂模式
 */
public final class IdempotentExecuteHandlerFactory {

    /**
     * 获取幂等执行处理器
     * @param scene 幂等场景
     * @param type 幂等类型
     * @return {@link IdempotentExecuteHandler}
     */
    public static IdempotentExecuteHandler getInstance(IdempotentSceneEnum scene, IdempotentTypeEnum type){
        IdempotentExecuteHandler result = null;
        switch(scene){
            case RESTAPI -> {
                switch(type){
                    case PARAM -> result = ApplicationContextHolder.getBean(IdempotentParamService.class);
                    case TOKEN -> result = ApplicationContextHolder.getBean(IdempotentTokenService.class);
                    case SPEL-> result = ApplicationContextHolder.getBean(IdempotentSpELByRestAPIExecuteHandler.class);
                    default -> {}
                }
            }
            case MQ -> result = ApplicationContextHolder.getBean(IdempotentSpELByMQExecuteHandler.class);
            default -> {}
        }
        return result;
    }
}
