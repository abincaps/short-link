package com.abincaps.shortlink.gateway.filter;

import cn.hutool.core.util.ObjectUtil;
import com.abincaps.shortlink.common.constant.TokenConstant;
import com.abincaps.shortlink.common.constant.UserConstant;
import com.abincaps.shortlink.common.utils.JwtUtils;
import com.abincaps.shortlink.gateway.config.Config;
import com.abincaps.shortlink.gateway.properties.TokenProperties;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Component
public class JwtValidateGatewayFilterFactory extends AbstractGatewayFilterFactory<Config> {

    @Resource
    private TokenProperties tokenProperties;

    public JwtValidateGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {

        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String requestPath = request.getPath().toString();

            // 放行在白名单内的请求路径
            if (isRequestPathInWhiteList(requestPath, config.getWhitePathList())) {
                return chain.filter(exchange);
            }

            String accessToken = request.getHeaders().getFirst(TokenConstant.ACCESS_TOKEN);

            // access_token 不存在
            if (ObjectUtil.isEmpty(accessToken)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            Claims claims = JwtUtils.checkToken(accessToken, tokenProperties.getSecretKey());

            // access_token 过期
            if (ObjectUtils.isEmpty(claims)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // 向下游微服务传递 user_id 参数
            exchange.getRequest().mutate().header(UserConstant.USER_ID, claims.get(UserConstant.USER_ID, String.class));

            return chain.filter(exchange);
        };
    }


    private boolean isRequestPathInWhiteList(String requestPath, List<String> whitePathList) {
        return (!CollectionUtils.isEmpty(whitePathList) && whitePathList.stream().anyMatch(requestPath::startsWith));
    }
}
