package com.jianhui.project.userservice.service.handler.user.filter;

import com.jianhui.project.framework.starter.convention.exception.ClientException;
import com.jianhui.project.userservice.common.enums.UserRegisterErrorCodeEnum;
import com.jianhui.project.userservice.dto.req.UserRegisterReqDTO;
import com.jianhui.project.userservice.service.UserLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户注册用户名唯一验证
 */
@Component
@RequiredArgsConstructor
public class UserRegisterHasUsernameChainHandler implements UserRegisterCreateChainFilter<UserRegisterReqDTO> {

    private final UserLoginService userLoginService;

    @Override
    public void handler(UserRegisterReqDTO requestParam) {
        if(!userLoginService.hasUsername(requestParam.getUsername())) {
            throw new ClientException(UserRegisterErrorCodeEnum.HAS_USERNAME_NOTNULL);
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
