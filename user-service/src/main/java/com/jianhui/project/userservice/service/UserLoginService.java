package com.jianhui.project.userservice.service;

import com.jianhui.project.userservice.dto.req.UserDeletionReqDTO;
import com.jianhui.project.userservice.dto.req.UserLoginReqDTO;
import com.jianhui.project.userservice.dto.req.UserRegisterReqDTO;
import com.jianhui.project.userservice.dto.resp.UserLoginRespDTO;
import com.jianhui.project.userservice.dto.resp.UserRegisterRespDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * 用户登录接口
 */
public interface UserLoginService {

    /**
     * 用户登录
     * @param requestParam 用户登录入参
     * @return 用户登录返回结果
     */
    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    /**
     * 通过 Token 检查用户是否登录
     * @param accessToken 用户登录 Token 凭证
     * @return 用户是否登录返回结果
     */
    UserLoginRespDTO checkLogin(String accessToken);

    /**
     * 用户退出登录
     * @param accessToken 用户登录 Token 凭证
     */
    void logout(String accessToken);

    /**
     * 用户名是否存在
     * @param username 用户名
     * @return 用户名是否存在返回结果 true为不存在,false为存在
     */
    Boolean hasUsername(@NotEmpty String username);

    /**
     * 用户注册
     * @param requestParam 用户注册入参
     * @return 用户注册返回结果
     */
    UserRegisterRespDTO register(@Valid UserRegisterReqDTO requestParam);

    /**
     * 注销用户
     * @param requestParam 注销用户入参
     */
    void deletion(@Valid UserDeletionReqDTO requestParam);
}
