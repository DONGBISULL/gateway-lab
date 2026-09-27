package com.gatewaylab.data.parking;

public record Parking(
		Long id,
		String name,
		String address,
		int capacity,
		int availableSpaces
) {
}
