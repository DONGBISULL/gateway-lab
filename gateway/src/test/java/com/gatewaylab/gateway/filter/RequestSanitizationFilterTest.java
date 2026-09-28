package com.gatewaylab.gateway.filter;

import com.gatewaylab.gateway.config.headers.GatewayHeadersProperties;
import com.gatewaylab.gateway.sanitize.HeaderRequestSanitizer;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSanitizationFilterTest {

    private static final GatewayHeadersProperties HEADERS = new GatewayHeadersProperties(
            "X-API-Key", "X-Client-Id", "X-Gateway-Passport", "X-Trace-Id"
    );

    private final RequestSanitizationFilter filter = new RequestSanitizationFilter(
            new HeaderRequestSanitizer(HEADERS)
    );

    @Test
    void 외부에서_보낸_내부_전용_헤더를_제거하고_인증_헤더는_유지한다() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/parkings")
                .header(HEADERS.clientId(), "forged-client")
                .header(HEADERS.passport(), "forged-passport")
                .header(HEADERS.traceId(), "forged-trace-id")
                .header(HEADERS.apiKey(), "api-key-for-authentication")
                .header("X-Request-Source", "external-client")
                .build();
        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            forwardedRequest.set(exchange.getRequest());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(MockServerWebExchange.from(request), chain))
                .verifyComplete();

        assertThat(forwardedRequest.get().getHeaders().getFirst(HEADERS.clientId())).isNull();
        assertThat(forwardedRequest.get().getHeaders().getFirst(HEADERS.passport())).isNull();
        assertThat(forwardedRequest.get().getHeaders().getFirst(HEADERS.traceId())).isNull();
        assertThat(forwardedRequest.get().getHeaders().getFirst(HEADERS.apiKey()))
                .isEqualTo("api-key-for-authentication");
        assertThat(forwardedRequest.get().getHeaders().getFirst("X-Request-Source"))
                .isEqualTo("external-client");
    }
}
