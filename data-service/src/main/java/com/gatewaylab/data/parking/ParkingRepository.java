package com.gatewaylab.data.parking;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ParkingRepository {

	private final Map<Long, Parking> parkings = List.of(
			new Parking(1L, "강남구청 공영주차장", "서울 강남구 학동로 426", 120, 34),
			new Parking(2L, "여의도공원 주차장", "서울 영등포구 여의공원로 68", 80, 0),
			new Parking(3L, "부산역 환승주차장", "부산 동구 중앙대로 206", 200, 157),
			new Parking(4L, "판교테크노밸리 공영주차장", "경기 성남시 분당구 판교로 242", 150, 12),
			new Parking(5L, "대전시청 지하주차장", "대전 서구 둔산로 100", 300, 88)
	).stream().collect(Collectors.toMap(Parking::id, p -> p));

	public List<Parking> findAll() {
		return parkings.values().stream()
				.sorted((a, b) -> Long.compare(a.id(), b.id()))
				.toList();
	}

	public Optional<Parking> findById(Long id) {
		return Optional.ofNullable(parkings.get(id));
	}
}
