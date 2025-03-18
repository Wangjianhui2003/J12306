package com.jianhui.project.userservice.controller;

import com.jianhui.project.framework.starter.convention.result.Result;
import com.jianhui.project.framework.starter.web.Results;
import com.jianhui.project.userservice.dto.req.UserLoginReqDTO;
import com.jianhui.project.userservice.dto.resp.UserLoginRespDTO;
import com.jianhui.project.userservice.service.UserLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户登录 Controller
 */
@RestController
@RequiredArgsConstructor
public class UserLoginController {

    public final UserLoginService userLoginService;

    /**
     * 用户登录
     */
    @PostMapping("/api/user-service/v1/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO requestParam) {
        return Results.success(userLoginService.login(requestParam));
    }

    /**
     * 通过 Token 检查用户是否登录
     */
    @GetMapping("/api/user-service/check-login")
    public Result<UserLoginRespDTO> checkLogin(@RequestParam("accessToken") String accessToken) {
        UserLoginRespDTO result = userLoginService.checkLogin(accessToken);
        return Results.success(result);
    }

    /**
     * 用户退出登录
     */
    @GetMapping("/api/user-service/logout")
    public Result<Void> logout(@RequestParam(required = false) String accessToken) {
        userLoginService.logout(accessToken);
        return Results.success();
    }

}
