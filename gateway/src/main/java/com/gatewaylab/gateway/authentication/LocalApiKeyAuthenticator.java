package com.gatewaylab.gateway.authentication;

import com.gatewaylab.gateway.config.authentication.ApiKeyProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Phase 2-1의 단일 API Key 검증기다.
 * 다음 단계에서는 이 컴포넌트를 Redis 캐시 + developer-service 조회 구현으로 교체한다.
 */
@Component
public class LocalApiKeyAuthenticator implements ApiKeyAuthenticator {

	private static final AuthenticatedPrincipal DEMO_CLIENT = new AuthenticatedPrincipal("demo-client", "API_KEY");
	private final ApiKeyProperties properties;

	public LocalApiKeyAuthenticator(ApiKeyProperties properties) {
		this.properties = properties;
	}

	@Override
	public Mono<AuthenticatedPrincipal> authenticate(String rawApiKey) {
		if (rawApiKey == null || !MessageDigest.isEqual(
				properties.apiKey().getBytes(StandardCharsets.UTF_8),
				rawApiKey.getBytes(StandardCharsets.UTF_8)
		)) {
			return Mono.empty();
		}
		return Mono.just(DEMO_CLIENT);
	}
}
