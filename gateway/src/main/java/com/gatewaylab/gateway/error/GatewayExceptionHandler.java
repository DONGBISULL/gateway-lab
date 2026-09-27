package com.gatewaylab.gateway.error;

import com.gatewaylab.gateway.json.JsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 필터/컨트롤러 어디서 던지든 {@link GatewayException}을 공통 오류 응답으로 내려준다.
 * Spring Boot 기본 {@code DefaultErrorWebExceptionHandler}(order -1)보다 먼저 타도록 -2로 둔다.
 */
@RequiredArgsConstructor
@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

	private static final String TRACE_ID_HEADER = "X-Trace-Id";
	private final JsonCodec jsonCodec;

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		if (!(ex instanceof GatewayException gatewayException)) {
			return Mono.error(ex);
		}

		GatewayErrorCode errorCode = gatewayException.errorCode();
		GatewayErrorResponse error = GatewayErrorResponse.of(
				errorCode,
				exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER)
		);
		byte[] body = jsonCodec.writeBytes(error);

		exchange.getResponse().setStatusCode(errorCode.status());
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
		return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
	}

}
