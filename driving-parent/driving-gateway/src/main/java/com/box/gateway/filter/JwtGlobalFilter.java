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
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtils jwtUtils;
    private static final Set<String> WHITE_LIST = Set.of("/api/auth/login");


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (WHITE_LIST.contains(path)){
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            Claims claims = jwtUtils.parseToken(token);
            if (claims == null) {
                return unauthorized(exchange, "登录已过期或身份无效");
            }

            String userId = String.valueOf(claims.get("userId", Long.class));
            String role   = claims.get("role", String.class);
            Object relatedId = claims.get("relatedId");

            if (userId == null || role == null) {
                return unauthorized(exchange, "身份信息不完整");
            }

            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(h -> {
                        h.set("X-User-Id", userId);
                        h.set("X-User-Role", role);
                        if (relatedId != null) {
                            h.set("X-User-RelatedId", String.valueOf(relatedId));
                        }
                    }))
                    .build();
            return chain.filter(mutated);

        }
        return unauthorized(exchange,"请先登录");
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String json = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(json.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

}
