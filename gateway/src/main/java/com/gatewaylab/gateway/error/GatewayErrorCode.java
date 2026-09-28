package com.gatewaylab.gateway.error;

import org.springframework.http.HttpStatus;

/**
 * Gateway가 외부로 내려주는 오류 코드와 메시지를 한 쌍으로 관리한다.
 * code만 바뀌고 message가 따라오지 않는 등의 불일치를 막기 위해 분리하지 않는다.
 */
public enum GatewayErrorCode {

	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A valid API key is required."),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The parking service is temporarily unavailable.");

	private final HttpStatus status;
	private final String message;

	GatewayErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public String code() {
		return name();
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}
}
