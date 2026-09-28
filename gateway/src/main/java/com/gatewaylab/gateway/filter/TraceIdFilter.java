package com.gatewaylab.gateway.filter;

import com.gatewaylab.gateway.config.headers.GatewayHeadersProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 외부 요청마다 Gateway가 신뢰 가능한 추적 ID를 발급하고 내부 서비스까지 전달한다.
 * 클라이언트가 보낸 {@code X-Trace-Id}는 신뢰하지 않고 항상 새 값으로 교체한다.
 *
 * <p>발급한 ID는 downstream 요청과 Gateway 응답 헤더에 함께 넣어, 클라이언트 로그와
 * 내부 서비스 로그를 같은 요청 단위로 연결할 수 있게 한다.</p>
 */
@Component
@Slf4j
public class TraceIdFilter implements GlobalFilter, Ordered {

    private final GatewayHeadersProperties headers;

    public TraceIdFilter(GatewayHeadersProperties headers) {
        this.headers = headers;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 외부 입력을 재사용하면 임의의 trace ID로 로그를 위조할 수 있으므로 Gateway가 직접 발급한다.
        String traceId = UUID.randomUUID().toString();
        // 단조 증가 시간으로 처리 시간을 측정한다. 시스템 시각 변경의 영향을 받지 않는다.
        long startedAt = System.nanoTime();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    // 같은 이름의 외부 헤더를 제거한 뒤 Gateway가 발급한 값만 downstream에 전달한다.
                    headers.remove(this.headers.traceId());
                    headers.set(this.headers.traceId(), traceId);
                })
                .build();

        // 오류 응답을 포함해 Gateway가 반환하는 모든 응답에서 추적 ID를 확인할 수 있게 한다.
        exchange.getResponse()
                .getHeaders()
                .set(headers.traceId(), traceId);

        return chain.filter(exchange.mutate()
                        .request(request)
                        .build())
                .doFinally(signalType -> log.info("traceId={} method={} path={} status={} elapsedMs={}",
                        traceId,
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI().getPath(),
                        exchange.getResponse().getStatusCode(),
                        (System.nanoTime() - startedAt) / 1_000_000));
    }

    @Override
    public int getOrder() {
        // ApiKeyAuthenticationFilter(-1)보다 먼저 실행되어 인증 실패 로그에도 trace ID가 남게 한다.
        return -2;
    }
}
