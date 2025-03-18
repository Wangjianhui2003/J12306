package com.jianhui.project.gatewayservice.filter;

import com.jianhui.project.framework.starter.bases.constant.UserConstant;
import com.jianhui.project.framework.starter.user.core.UserInfoDTO;
import com.jianhui.project.framework.starter.user.toolkit.JWTUtil;
import com.jianhui.project.gatewayservice.config.Config;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;


/**
 * SpringCloud Gateway Token 拦截器
 */
@Component
public class TokenValidateGatewayFilterFactory extends AbstractGatewayFilterFactory<Config> {

    /**
     * 注销用户时需要传递 Token
     */
    public static final String DELETION_PATH = "/api/user-service/deletion";

    public TokenValidateGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String requestPath = request.getPath().toString();
//            如果在黑名单
            if(isPathInBlackPrefixList(requestPath,config.getBlackPathPrefix())) {
                String token = request.getHeaders().getFirst("Authorization");
                UserInfoDTO userInfoDTO = JWTUtil.parseJwtToken(token);
                if (userInfoDTO == null) {
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return response.setComplete();
                }

                ServerHttpRequest request2 = exchange.getRequest().mutate().headers(httpHeaders -> {
                    httpHeaders.set(UserConstant.USER_ID_KEY, userInfoDTO.getUserId());
                    httpHeaders.set(UserConstant.USER_NAME_KEY, userInfoDTO.getUsername());
//                    将名字编码
                    httpHeaders.set(UserConstant.REAL_NAME_KEY, URLEncoder.encode(
                            userInfoDTO.getRealName(), StandardCharsets.UTF_8));
//                    是注销用户时
                    if (requestPath.equals(DELETION_PATH)) {
                        httpHeaders.set(UserConstant.USER_TOKEN_KEY, token);
                    }
                }).build();

                return chain.filter(exchange.mutate().request(request2).build());
            }
            return chain.filter(exchange);
        };
    }

    private boolean isPathInBlackPrefixList(String requestPath, List<String> blackPathPrefix) {
        if (CollectionUtils.isEmpty(blackPathPrefix)) {
            return false;
        }
        return blackPathPrefix.stream().anyMatch(requestPath::startsWith);
    }
}
