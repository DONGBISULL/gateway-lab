package com.gatewaylab.gateway.config.headers;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Gateway가 읽거나 발급하는 HTTP 헤더 이름 모음이다. */
@ConfigurationProperties(prefix = "app.headers")
public record GatewayHeadersProperties(
        String apiKey,
        String clientId,
        String passport,
        String traceId
) {
}
