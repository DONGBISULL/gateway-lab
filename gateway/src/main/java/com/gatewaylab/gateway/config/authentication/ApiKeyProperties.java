package com.gatewaylab.gateway.config.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 로컬 개발 단계의 API Key 검증 설정이다. */
@ConfigurationProperties(prefix = "app.authentication")
public record ApiKeyProperties(String apiKey) {
}
