package com.gatewaylab.gateway.passport;

import com.gatewaylab.gateway.authentication.AuthenticatedPrincipal;
import com.gatewaylab.gateway.config.headers.GatewayHeadersProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;

/**
 * 인증된 호출자에게 Gateway Passport를 발급해 downstream에 신뢰 헤더를 추가한다.
 */
@Component
@RequiredArgsConstructor
public class GatewayPassportIssuer {

    private final GatewayHeadersProperties headers;
    private final GatewayPassportCodec passportCodec;

    public ServerWebExchange issueTo(ServerWebExchange exchange, AuthenticatedPrincipal principal) {
        String traceId = exchange.getRequest()
                .getHeaders()
                .getFirst(headers.traceId());
        String passport = passportCodec.encode(new GatewayPassport(
                principal.clientId(), principal.authenticationType(), Instant.now(), traceId
        ));

        ServerHttpRequest trustedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(this.headers.apiKey());
                    headers.set(this.headers.clientId(), principal.clientId());
                    headers.set(this.headers.passport(), passport);
                })
                .build();

        return exchange.mutate().request(trustedRequest).build();
    }
}
