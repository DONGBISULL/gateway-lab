package com.gatewaylab.gateway.json;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;


/**
 * Jackson을 이용한 JSON 바이트 변환을 공통으로 제공한다.
 * 포맷별 인코딩(Base64, JWT 등)은 각 codec이 담당한다.
 */
@Component
@RequiredArgsConstructor
public class JsonCodec {

	private final ObjectMapper objectMapper;

	public byte[] writeBytes(Object value) {
		try {
			return objectMapper.writeValueAsBytes(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize JSON", exception);
		}
	}

	public <T> T read(byte[] json, Class<T> type) {
		try {
			return objectMapper.readValue(json, type);
		} catch (JacksonException exception) {
			throw new IllegalArgumentException("Failed to deserialize JSON", exception);
		}
	}
}
