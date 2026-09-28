package com.gatewaylab.gateway.filter;

import com.gatewaylab.gateway.sanitize.RequestSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 필터 체인에서 가장 먼저 요청을 정제한다. 뒤에 오는 모든 필터(TraceId, 인증 등)는
 * 이 단계를 거친 요청이 이미 신뢰할 수 있는 상태라고 가정하고 동작한다.
 */
@Component
@RequiredArgsConstructor
public class RequestSanitizationFilter implements GlobalFilter, Ordered {

    private final RequestSanitizer sanitizer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitized = exchange.mutate()
                .request(sanitizer.sanitize(exchange.getRequest()))
                .build();
        return chain.filter(sanitized);
    }

    @Override
    public int getOrder() {
        // TraceIdFilter(-2), ApiKeyAuthenticationFilter(-1)보다 먼저 돌아야 한다.
        return -3;
    }
}
