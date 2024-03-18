package com.nageoffer.shortlink.gateway.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @Author abincaps
 * @Date 3/17/2024 12:50 AM
 * @Description
 */

@Component
@ConfigurationProperties(prefix = "short-link.token")
@Data
public class TokenProperties {
    /**
     * accessToken 过期秒数
     */
    private int accessTokenTtl;
    /**
     * refreshToken 过期秒数
     */
    private int refreshTokenTtl;

    /**
     * 密钥
     */
    private String secretKey;
}
