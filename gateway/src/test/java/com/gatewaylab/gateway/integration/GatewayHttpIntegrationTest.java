package com.gatewaylab.gateway.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayHttpIntegrationTest {

	private static final String TEST_API_KEY = UUID.randomUUID().toString();

	@LocalServerPort
	private int port;

	private WebTestClient client;

	@DynamicPropertySource
	static void gatewayProperties(DynamicPropertyRegistry registry) {
		registry.add("app.authentication.api-key", () -> TEST_API_KEY);
		registry.add("spring.data.redis.host", () -> "localhost");
		registry.add("spring.data.redis.port", () -> 6379);
		registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "unavailable-parkings");
		registry.add("spring.cloud.gateway.server.webflux.routes[0].uri", () -> "http://127.0.0.1:1");
		registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]", () -> "Path=/api/v1/parkings/**");
		registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].name", () -> "CircuitBreaker");
		registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].args.name", () -> "testParkings");
		registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].args.fallbackUri", () -> "forward:/fallback/parkings");
	}

	@BeforeEach
	void setUp() {
		client = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
	}

	@Test
	void apiKey가_없으면_추적_ID와_함께_401을_반환한다() {
		client.get()
				.uri("/api/v1/parkings")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().exists("X-Trace-Id")
				.expectHeader().contentType("application/json")
				.expectBody()
				.jsonPath("$.code").isEqualTo("UNAUTHORIZED")
				.jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.traceId").isNotEmpty()
				.jsonPath("$.timestamp").isNotEmpty();
	}

	@Test
	void 유효한_API_Key로_장애난_서비스를_호출하면_503_fallback을_반환한다() {
		client.get()
				.uri("/api/v1/parkings")
				.header("X-API-Key", TEST_API_KEY)
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectHeader().exists("X-Trace-Id")
				.expectBody()
				.jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
				.jsonPath("$.status").isEqualTo(503)
				.jsonPath("$.traceId").isNotEmpty();
	}
}
