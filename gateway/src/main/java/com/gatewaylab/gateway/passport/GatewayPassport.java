package com.gatewaylab.gateway.passport;

import java.time.Instant;

/**
 * Gateway가 인증을 마친 뒤 내부 서비스에만 전파하는 호출자 컨텍스트다.
 * 외부 API의 요청/응답 모델과 분리하며, 클라이언트가 직접 신뢰할 수 있는 값이 아니다.
 */
public record GatewayPassport(
        /** 클라이언트 ID · 예: demo-client */
        String clientId,
        /** 인증 방식 · 예: API_KEY */
        String authenticationType,
        /** 발급 시각(UTC) · 예: 2026-09-27T11:20:00Z */
        Instant issuedAt,
        /** 추적 ID · 예: 550e8400-e29b-41d4-a716-446655440000 */
        String traceId
) {
}
