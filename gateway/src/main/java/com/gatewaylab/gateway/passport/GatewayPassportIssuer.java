package com.gatewaylab.gateway.passport;

import com.gatewaylab.gateway.authentication.AuthenticatedPrincipal;
import com.gatewaylab.gateway.config.passport.PassportProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;

/**
 * 인증된 호출자에게 Gateway Passport를 발급해 downstream이 신뢰할 X-Client-Id / X-Gateway-Passport로 교체한다.
 * 외부에서 같은 이름의 헤더를 보내도 위조 가능하므로 항상 제거하고, Gateway가 발급한 값만 남긴다.
 */
@Component
public class GatewayPassportIssuer {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String CLIENT_ID_HEADER = "X-Client-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final PassportProperties passportProperties;
    private final GatewayPassportCodec passportCodec;

    public GatewayPassportIssuer(PassportProperties passportProperties, GatewayPassportCodec passportCodec) {
        this.passportProperties = passportProperties;
        this.passportCodec = passportCodec;
    }

    public ServerWebExchange issueTo(ServerWebExchange exchange, AuthenticatedPrincipal principal) {
        String traceId = exchange.getRequest()
                .getHeaders()
                .getFirst(TRACE_ID_HEADER);
        String passport = passportCodec.encode(new GatewayPassport(
                principal.clientId(), principal.authenticationType(), Instant.now(), traceId
        ));

        ServerHttpRequest trustedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    // 클라이언트가 같은 이름으로 위조해 보냈을 수 있으니 먼저 지우고, Gateway가 발급한 값만 남긴다.
                    headers.remove(CLIENT_ID_HEADER);
                    headers.remove(passportProperties.headerName());
                    headers.remove(API_KEY_HEADER);
                    headers.set(CLIENT_ID_HEADER, principal.clientId());
                    headers.set(passportProperties.headerName(), passport);
                })
                .build();

        return exchange.mutate().request(trustedRequest).build();
    }
}
