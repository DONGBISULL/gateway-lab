package com.gatewaylab.gateway.authentication;

import reactor.core.publisher.Mono;

/**
 * API Key를 검증하고 신뢰할 수 있는 호출자 정보를 반환하는 인증 경계다.
 *
 * <p>Gateway 필터는 이 계약에만 의존한다. 로컬 개발용 비교 구현은 물론,
 * Redis 캐시와 developer-service를 사용하는 구현도 같은 계약으로 교체할 수 있다.</p>
 */
public interface ApiKeyAuthenticator {

	/** 유효하지 않거나 폐기된 키는 빈 Mono로 반환한다. 원문 API Key는 반환하거나 로그에 남기지 않는다. */
	Mono<AuthenticatedPrincipal> authenticate(String rawApiKey);
}
