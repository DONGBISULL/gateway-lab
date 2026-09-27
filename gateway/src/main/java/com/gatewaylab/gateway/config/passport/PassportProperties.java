package com.gatewaylab.gateway.config.passport;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 내부 서비스에 전파하는 Passport 헤더 설정이다. */
@ConfigurationProperties(prefix = "app.passport")
public record PassportProperties(String headerName) {
}
