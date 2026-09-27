package com.gatewaylab.gateway.authentication;

/**
 * 현재 요청에서 인증을 통과한 호출 주체의 신원이다.
 *
 * @param clientId 호출 애플리케이션의 식별자
 * @param authenticationType 인증에 성공한 방식이다. 예: {@code API_KEY}.
 *                           원문 API Key, access token 등 민감한 인증 정보는 절대 담지 않는다.
 */
public record AuthenticatedPrincipal(String clientId, String authenticationType) {
}
