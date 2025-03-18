package com.jianhui.project.framework.starter.user.toolkit;

import com.alibaba.fastjson2.JSON;
import com.jianhui.project.framework.starter.user.core.UserInfoDTO;
import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.jianhui.project.framework.starter.bases.constant.UserConstant.*;

@Slf4j
public final class JWTUtil {
    private static final long EXPIRATION = 86400L;
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String ISS = "j12306";
    public static final String SECRET = "SecretKey039245678901232039487623456783092349288901402967890140939827";

    /**
     * 生成用户token
     * @param userInfo 用户信息
     * @return 用户访问token
     */
    public static String generateAccessToken(UserInfoDTO userInfo) {
        Map<Object, Object> customerUserMap = new HashMap<>();
        customerUserMap.put(USER_ID_KEY, userInfo.getUserId());
        customerUserMap.put(USER_NAME_KEY, userInfo.getUsername());
        customerUserMap.put(REAL_NAME_KEY, userInfo.getRealName());
        String jwtToken = Jwts.builder()
                .signWith(SignatureAlgorithm.HS512, SECRET)
                .setIssuedAt(new Date())
                .setIssuer(ISS)
                .setSubject(JSON.toJSONString(customerUserMap))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION * 1000))
                .compact();
        return TOKEN_PREFIX + jwtToken;
    }

    /**
     * 解析用户token token -> json -> UserInfoDTO
     * @param jwtToken 用户访问token
     * @return 用户信息
     */
    public static UserInfoDTO parseJwtToken(String jwtToken) {
        if(StringUtils.hasText(jwtToken)){
            String actualJwtToken = jwtToken.replace(TOKEN_PREFIX, "");
            try {
                Claims claims = Jwts.parser()
                        .setSigningKey(SECRET)
                        .parseClaimsJws(actualJwtToken)
                        .getBody();
                Date expiration = claims.getExpiration();
                if(expiration.after(new Date())){
                    String subject = claims.getSubject();
                    return JSON.parseObject(subject, UserInfoDTO.class);
                }
            } catch (ExpiredJwtException ignored) {
            } catch (Exception ex) {
                log.error("解析用户JWT Token失败:", ex);
            }
        }
        return null;
    }
}
