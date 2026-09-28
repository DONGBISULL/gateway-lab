package com.gatewaylab.gateway.integration;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayHeaderPropagationIntegrationTest {

	private static final String TEST_API_KEY = UUID.randomUUID().toString();
	private static final AtomicReference<Headers> DOWNSTREAM_HEADERS = new AtomicReference<>();
	private static final HttpServer DOWNSTREAM = startDownstream();

	@LocalServerPort
	private int port;

	private WebTestClient client;

	@DynamicPropertySource
	static void gatewayProperties(DynamicPropertyRegistry registry) {
		registry.add("app.authentication.api-key", () -> TEST_API_KEY);
		registry.add("spring.data.redis.host", () -> "localhost");
		registry.add("spring.data.redis.port", () -> 6379);
		registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "header-propagation");
		registry.add("spring.cloud.gateway.server.webflux.routes[0].uri",
				() -> "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
		registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]", () -> "Path=/api/v1/parkings/**");
	}

	@BeforeEach
	void setUp() {
		DOWNSTREAM_HEADERS.set(null);
		client = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
	}

	@AfterAll
	static void stopDownstream() {
		DOWNSTREAM.stop(0);
	}

	@Test
	void Gateway가_위조된_내부_헤더를_교체하고_동일한_trace_ID를_downstream에_전파한다() {
		String forgedTraceId = "forged-trace-id";
		EntityExchangeResult<byte[]> response = client.get()
				.uri("/api/v1/parkings")
				.header("X-API-Key", TEST_API_KEY)
				.header("X-Client-Id", "forged-client")
				.header("X-Gateway-Passport", "forged-passport")
				.header("X-Trace-Id", forgedTraceId)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().exists("X-Trace-Id")
				.expectBody(byte[].class)
				.returnResult();

		String responseTraceId = response.getResponseHeaders().getFirst("X-Trace-Id");
		Headers downstreamHeaders = DOWNSTREAM_HEADERS.get();

		assertNotNull(downstreamHeaders, "downstream 요청이 수신되어야 한다");
		assertNotEquals(forgedTraceId, responseTraceId);
		UUID.fromString(responseTraceId);
		assertEquals(responseTraceId, downstreamHeaders.getFirst("X-Trace-Id"));
		assertEquals("demo-client", downstreamHeaders.getFirst("X-Client-Id"));
		assertNotEquals("forged-passport", downstreamHeaders.getFirst("X-Gateway-Passport"));
		assertFalse(downstreamHeaders.getFirst("X-Gateway-Passport").isBlank());
		assertNull(downstreamHeaders.getFirst("X-API-Key"));
	}

	private static HttpServer startDownstream() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				Headers headers = new Headers();
				headers.putAll(exchange.getRequestHeaders());
				DOWNSTREAM_HEADERS.set(headers);

				byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});
			server.start();
			return server;
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to start test downstream server", exception);
		}
	}
}
