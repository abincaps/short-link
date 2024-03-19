package com.nageoffer.shortlink.gateway.filter;

import cn.hutool.core.util.ObjectUtil;
import com.abincaps.shortlink.common.constant.TokenConstant;
import com.nageoffer.shortlink.gateway.config.Config;
import com.nageoffer.shortlink.gateway.properties.TokenProperties;
import com.nageoffer.shortlink.gateway.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
public class JwtValidateGatewayFilterFactory extends AbstractGatewayFilterFactory<Config> {

    @Resource
    private TokenProperties tokenProperties;

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

            Claims claims = JwtUtils.checkToken(accessToken, tokenProperties.getSecretKey());

            if (ObjectUtil.isEmpty(claims)) {
                //token过期或伪造，响应401
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // 向下游微服务传递参数
            exchange.getRequest().mutate().header("user_id", claims.get("user_id", String.class));

            return chain.filter(exchange);
        };
    }


    private boolean isRequestPathInWhiteList(String requestPath, List<String> whitePathList) {
        return (!CollectionUtils.isEmpty(whitePathList) && whitePathList.stream().anyMatch(requestPath::startsWith));
    }
}
