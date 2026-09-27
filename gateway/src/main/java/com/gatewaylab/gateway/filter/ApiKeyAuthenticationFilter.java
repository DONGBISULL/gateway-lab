package com.gatewaylab.gateway.filter;

import com.gatewaylab.gateway.authentication.ApiKeyAuthenticator;
import com.gatewaylab.gateway.error.GatewayErrorCode;
import com.gatewaylab.gateway.error.GatewayException;
import com.gatewaylab.gateway.passport.GatewayPassportIssuer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * X-API-Key로 호출자를 인증하는 문지기 역할만 한다.
 * 통과한 요청에 downstream 신뢰 헤더를 붙이는 일은 {@link GatewayPassportIssuer}에게 맡긴다.
 */
@Component
public class ApiKeyAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String API_KEY_HEADER = "X-API-Key";

    // 2-1 단계: 키 1개짜리 임시 검증. 값은 .env(API_KEY)에서만 가져온다 — 코드/커밋 대상 설정엔 두지 않는다.
    // 2-3 단계에서 developer-service 조회로 교체한다.
    private final ApiKeyAuthenticator authenticator;
    private final GatewayPassportIssuer passportIssuer;

    public ApiKeyAuthenticationFilter(ApiKeyAuthenticator authenticator, GatewayPassportIssuer passportIssuer) {
        this.authenticator = authenticator;
        this.passportIssuer = passportIssuer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 헬스체크 등 내부 모니터링 경로는 API Key 없이도 통과시킨다.
        if (isExcluded(exchange)) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest()
                .getHeaders()
                .getFirst(API_KEY_HEADER);

        // 인증 실패는 401, 성공 요청에는 내부 신뢰 헤더를 발급한다.
        return authenticator.authenticate(apiKey)
                .switchIfEmpty(Mono.error(new GatewayException(GatewayErrorCode.UNAUTHORIZED)))
                .map(principal -> passportIssuer.issueTo(exchange, principal))
                .flatMap(chain::filter);
    }

    private boolean isExcluded(ServerWebExchange exchange) {
        return exchange.getRequest().getURI().getPath().startsWith("/actuator");
    }

    @Override
    public int getOrder() {
        // TraceIdFilter(-2) 다음으로 돈다 — traceId는 이미 있어야 하지만, 인증은 실제 라우팅보다는 앞서야 한다.
        return -1;
    }
}
