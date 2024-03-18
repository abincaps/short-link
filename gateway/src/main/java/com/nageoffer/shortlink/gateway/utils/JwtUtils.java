package com.nageoffer.shortlink.gateway.utils;

import com.alibaba.fastjson2.util.DateUtils;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jcajce.provider.asymmetric.RSA;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Author abincaps
 * @Date 3/16/2024 11:33 PM
 * @Description
 */

@Slf4j
public class JwtUtils {

    /**
     * 生成token
     *
     * @param claims        payload
     * @param secretKey     密钥
     * @param offsetSeconds 过期时间偏移量
     * @return token
     */
    public static String createToken(Map<String, Object> claims, String secretKey , int offsetSeconds) {

        return Jwts.builder()
                .claims(claims)
                .expiration(Date.from(Instant.now().plusSeconds(offsetSeconds)))
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes()), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 校验token
     *
     * @param token        token字符串
     * @param secretKey    密钥
     * @return Payload
     */
    public static Claims checkToken(String token, String secretKey) {

        try {
            return Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes()))
                    .build()
                    .parseSignedClaims(token).getPayload();

        } catch (JwtException e) {
            log.error("过期token = {}", token);
        } catch (Exception e) {
            log.error("非法token = {}", token);
        }

        return null;
    }
}
