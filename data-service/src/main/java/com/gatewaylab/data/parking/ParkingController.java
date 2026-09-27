package com.gatewaylab.data.parking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ParkingController {

	private final ParkingRepository parkingRepository;

	public ParkingController(ParkingRepository parkingRepository) {
		this.parkingRepository = parkingRepository;
	}

	@GetMapping("/parkings")
	public List<Parking> findAll() {
		return parkingRepository.findAll();
	}

	@GetMapping("/parkings/{id}")
	public ResponseEntity<Parking> findById(@PathVariable Long id) {
		return parkingRepository.findById(id)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
