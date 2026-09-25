# 02. 마이크로서비스 (MSA)

## 1. 한 줄 정의

하나의 큰 애플리케이션을 **독립적으로 배포 가능한 작은 서비스들**로 나누고,
서비스끼리는 네트워크(HTTP, 메시지)로 통신하는 구조.

## 2. 모놀리식 vs 마이크로서비스

| 항목 | 모놀리식 | 마이크로서비스 |
|---|---|---|
| 배포 | 전체를 한 번에 | 서비스별 따로 |
| DB | 하나를 공유 | 서비스별 소유 (원칙) |
| 장애 | 한 곳 장애가 전체로 번지기 쉬움 | 격리 가능 (대신 설계 필요) |
| 개발 초기 속도 | 빠름 | 느림 (인프라 준비가 많음) |
| 복잡도 | 코드 내부에 있음 | 네트워크·운영으로 옮겨감 |
| 트랜잭션 | DB 트랜잭션으로 간단 | 분산 트랜잭션 문제 발생 |

> 핵심: MSA는 "좋은 구조"가 아니라 **트레이드오프**. 조직/규모가 커서 독립 배포가 필요할 때 이득이 크다.

## 3. 공부할 영역 (순서대로)

### 3-1. 서비스 분리 기준
- [ ] 도메인 단위로 나누기 — 이 실습에서는: 토큰 발급(auth) / 개발자·앱 관리(developer) / 공개 데이터(data) / 사용량(usage)
- [ ] "바뀌는 이유가 다르면 나눈다": 인증 정책 변경과 주차장 데이터 변경은 서로 영향이 없어야 함
- [ ] DDD의 **Bounded Context** 개념 맛보기
- [ ] 너무 잘게 나누면 생기는 문제 (Nano service)

### 3-2. Service Discovery
- [ ] 서비스 주소를 하드코딩하지 않고 **이름으로 찾기**
- [ ] Eureka Server / Client 동작 (등록, heartbeat, 조회)
- [ ] Client-side LB vs Server-side LB

### 3-3. 서비스 간 통신
- [ ] **동기**: REST (RestClient / WebClient / OpenFeign)
- [ ] **비동기**: 메시지 브로커 (Kafka / RabbitMQ) — 이벤트 발행/구독
- [ ] 언제 동기, 언제 비동기를 쓰는지

### 3-4. 장애 대응 (Resilience)
- [ ] Timeout, Retry
- [ ] **Circuit Breaker** (Closed → Open → Half-Open 상태 전이)
- [ ] Fallback
- [ ] 장애 전파(Cascading Failure) 막기

### 3-5. 데이터 관리
- [ ] **Database per Service** 원칙
- [ ] 다른 서비스 데이터가 필요할 때: API 호출 vs 데이터 복제
- [ ] 분산 트랜잭션 문제 → **Saga 패턴** (Choreography / Orchestration)
- [ ] 최종적 일관성 (Eventual Consistency)

### 3-6. 관측성 (Observability)
- [ ] 요청 ID(Trace ID)로 여러 서비스 로그 연결하기
- [ ] 분산 추적: Micrometer Tracing + Zipkin
- [ ] 헬스체크: Spring Boot Actuator

### 3-7. 설정 / 배포
- [ ] 설정 중앙화: Spring Cloud Config
- [ ] 컨테이너화: Dockerfile, Docker Compose
- [ ] (선택) Kubernetes 에서는 Service/Ingress가 Discovery·Gateway 역할 일부를 대신함

## 4. 스스로 답해보기

1. 앱 등록 시 developer-service 저장은 성공했는데 auth-server client 등록이 실패하면? (분산 트랜잭션)
2. usage-service가 죽었을 때 공개 API 호출까지 실패하면 안 되는 이유와 방법은?
3. Eureka를 쓰는데 왜 Gateway에서 `lb://` 를 쓰나?

## 내 메모

