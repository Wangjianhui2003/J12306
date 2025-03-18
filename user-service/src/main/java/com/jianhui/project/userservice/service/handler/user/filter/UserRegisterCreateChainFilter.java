package com.jianhui.project.userservice.service.handler.user.filter;

import com.jianhui.project.frameworks.starter.designpattern.chain.AbstractChainHandler;
import com.jianhui.project.userservice.common.enums.UserChainMarkEnum;
import com.jianhui.project.userservice.dto.req.UserRegisterReqDTO;

/**
 * 用户注册责任链过滤器
 * @param <T>
 */
public interface UserRegisterCreateChainFilter<T extends UserRegisterReqDTO> extends AbstractChainHandler<UserRegisterReqDTO> {

    @Override
    default String mark() {
        return UserChainMarkEnum.USER_REGISTER_FILTER.name();
    }
}
