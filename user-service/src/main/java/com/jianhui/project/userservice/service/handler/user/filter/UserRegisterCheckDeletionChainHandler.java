package com.jianhui.project.userservice.service.handler.user.filter;

import com.jianhui.project.framework.starter.convention.exception.ClientException;
import com.jianhui.project.userservice.dto.req.UserRegisterReqDTO;
import com.jianhui.project.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * 用户注册检查用户注销次数责任链
 */

@Component
@RequiredArgsConstructor
public class UserRegisterCheckDeletionChainHandler implements UserRegisterCreateChainFilter<UserRegisterReqDTO> {

    private final UserService userService;

    @Override
    public void handler(UserRegisterReqDTO requestParam) {
        Integer deletionNum = userService.queryUserDeletionNum(requestParam.getIdType(), requestParam.getIdCard());
        if (deletionNum >= 5) {
            throw new ClientException("账号注销次数过多(>=5),已被加入黑名单");
        }
    }

    @Override
    public int getOrder() {
        return 2;
    }
}
