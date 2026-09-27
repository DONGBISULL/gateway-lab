package com.gatewaylab.gateway.error;

/** Gateway 필터/컨트롤러에서 공통 오류 응답으로 변환돼야 하는 예외다. */
public class GatewayException extends RuntimeException {

	private final GatewayErrorCode errorCode;

	public GatewayException(GatewayErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public GatewayErrorCode errorCode() {
		return errorCode;
	}
}
