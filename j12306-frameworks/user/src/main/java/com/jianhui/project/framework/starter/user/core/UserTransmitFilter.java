package com.jianhui.project.framework.starter.user.core;

import com.jianhui.project.framework.starter.bases.constant.UserConstant;
import io.jsonwebtoken.lang.Strings;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 用户信息传输过滤器
 * 从请求头中获取用户信息并存入ThreadLocal
 * 请求结束后清除UserInfoDTO
 */
public class UserTransmitFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
//        从请求头中获取用户ID
        String userId = request.getHeader(UserConstant.USER_ID_KEY);
        if(Strings.hasText(userId)){
            String username = request.getHeader(UserConstant.USER_NAME_KEY);
            String realName = request.getHeader(UserConstant.REAL_NAME_KEY);
//            TODO:为什么要解码
            if (Strings.hasText(username)) {
                username = URLDecoder.decode(username, StandardCharsets.UTF_8);
            }
            if (Strings.hasText(realName)) {
                realName = URLDecoder.decode(realName, StandardCharsets.UTF_8);
            }
            UserInfoDTO userInfoDTO = UserInfoDTO.builder()
                    .userId(userId)
                    .username(username)
                    .realName(realName)
                    .build();
            UserContext.setUser(userInfoDTO);
            try {
//                filter链
                filterChain.doFilter(servletRequest, servletResponse);
            } finally {
//                请求结束清除ThreadLocal
                UserContext.remove();
            }
        }
    }
}
