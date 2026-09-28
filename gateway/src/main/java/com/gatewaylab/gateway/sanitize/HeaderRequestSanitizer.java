package com.gatewaylab.gateway.sanitize;

import com.gatewaylab.gateway.config.headers.GatewayHeadersProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * Gateway만 발급해야 하는 헤더(X-Client-Id, X-Gateway-Passport, X-Trace-Id)를 제거한다.
 * 이 값들은 뒤에 오는 필터가 인증/추적 결과로 직접 채우므로, 클라이언트가 보낸 값을 신뢰하면 안 된다.
 */
@Component
@RequiredArgsConstructor
public class HeaderRequestSanitizer implements RequestSanitizer {

    private final GatewayHeadersProperties headers;

    @Override
    public ServerHttpRequest sanitize(ServerHttpRequest request) {
        return request.mutate()
                .headers(httpHeaders -> {
                    httpHeaders.remove(headers.clientId());
                    httpHeaders.remove(headers.passport());
                    httpHeaders.remove(headers.traceId());
                })
                .build();
    }
}
