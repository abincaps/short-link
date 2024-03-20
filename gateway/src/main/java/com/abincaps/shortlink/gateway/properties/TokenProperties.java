package com.abincaps.shortlink.gateway.properties;

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
     * 密钥
     */
    private String secretKey;
}
