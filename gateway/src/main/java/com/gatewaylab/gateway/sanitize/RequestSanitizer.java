package com.gatewaylab.gateway.sanitize;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * downstream이 신뢰해서는 안 되는 요청값을 제거하거나 올바른 값으로 바꾼다.
 * Gateway 필터 체인에서 가장 먼저 실행돼야 한다 — 이후 단계는 이미 정제된 요청이라고 가정하고 동작한다.
 */
public interface RequestSanitizer {

    ServerHttpRequest sanitize(ServerHttpRequest request);
}
