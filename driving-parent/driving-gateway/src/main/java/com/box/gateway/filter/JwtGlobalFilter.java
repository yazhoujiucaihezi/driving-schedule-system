package com.box.gateway.filter;

import com.box.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtils jwtUtils;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 获取请求路径
        String path = exchange.getRequest().getURI().getPath();
        // 判断是否为白名单
        if (path.equals("/api/auth/login")) {
            return chain.filter(exchange);
        }
        // 获取 Authorization 头
        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        // 判断 Authorization 头是否存在且格式是否正确
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        // 取出token
        String token = authorization.substring(7);
        // 验证 token
            if (!jwtUtils.validateToken(token)){
                return unauthorized(exchange);
            }
            Claims claims = jwtUtils.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String role = claims.get("role", String.class);

            // 4. 塞到请求头
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r
                            .header("X-User-Id", String.valueOf(userId))
                            .header("X-User-Role", role))
                    .build();

            return chain.filter(mutated);

    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"未登录或 Token 失效\",\"data\":null}";
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

