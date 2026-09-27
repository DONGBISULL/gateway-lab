package com.gatewaylab.gateway.fallback;

import com.gatewaylab.gateway.error.GatewayErrorCode;
import com.gatewaylab.gateway.error.GatewayException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class FallbackController {

	@GetMapping("/fallback/parkings")
	public Mono<Void> parkingsUnavailable() {
		return Mono.error(new GatewayException(GatewayErrorCode.SERVICE_UNAVAILABLE));
	}
}
