package com.gatewaylab.gateway.error;

import java.time.Instant;

/**
 * Gateway가 외부 클라이언트에 반환하는 공통 오류 응답 계약이다.
 * 내부 서비스명, 예외 메시지, stack trace는 외부에 노출하지 않는다.
 */
public record GatewayErrorResponse(
		String code,
		String message,
		int status,
		String traceId,
		Instant timestamp
) {
	public static GatewayErrorResponse of(GatewayErrorCode errorCode, String traceId) {
		return new GatewayErrorResponse(errorCode.code(), errorCode.message(), errorCode.status().value(), traceId, Instant.now());
	}
}
