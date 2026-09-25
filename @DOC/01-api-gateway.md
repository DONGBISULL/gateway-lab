# 01. API Gateway

## 1. 한 줄 정의

클라이언트와 여러 백엔드 서비스 사이에 있는 **단일 진입점(Single Entry Point)**.
모든 요청은 Gateway를 거쳐 알맞은 서비스로 전달된다.

## 2. 왜 필요한가

Gateway가 없으면:

```
Client ──▶ user-service   (인증 코드)
Client ──▶ order-service  (인증 코드 또 작성)
Client ──▶ pay-service    (인증 코드 또 작성)
```

- 클라이언트가 서비스 주소를 전부 알아야 함
- 인증/로깅/CORS 같은 공통 기능을 서비스마다 중복 구현
- 내부 서비스가 외부에 그대로 노출됨

Gateway가 있으면:

```
Client ──▶ Gateway ──▶ user-service
                  ├──▶ order-service
                  └──▶ pay-service
```

- 클라이언트는 Gateway 주소 하나만 앎
- 공통 기능은 Gateway에서 한 번만 처리
- 내부 서비스는 숨길 수 있음

## 3. Gateway의 주요 역할 (공부 체크리스트)

- [ ] **라우팅 (Routing)**: `/api/v1/parkings/**` → data-service
- [ ] **인증 (Authentication)**: API Key 또는 JWT 검증 → 누가 호출했는지 확인 (자세한 내용: [03-open-api-auth.md](03-open-api-auth.md))
- [ ] **인가 (Authorization)**: scope 확인 → 이 API를 호출할 권한이 있는지
- [ ] **client별 Rate Limit / Quota**: 요금제에 따라 초당·일일 호출 제한 (Redis)
- [ ] **호출 로그 / 사용량 기록**: 누가 언제 무엇을 호출했는지 → 통계·과금 기초 데이터
- [ ] **로깅 / 모니터링**: 요청·응답 시간, 요청 ID(traceId) 부여
- [ ] **Circuit Breaker**: 뒤쪽 서비스 장애 시 빠르게 실패 + fallback 응답
- [ ] **Load Balancing**: 같은 서비스 인스턴스 여러 개에 분산
- [ ] **CORS / 헤더 가공**: 공통 헤더 추가·제거
- [ ] **경로 재작성 (Rewrite)**: `/api/v1/parkings/1` → `/parkings/1`
- [ ] **표준 에러 응답**: 401/403/429/503 을 모든 API에서 같은 JSON 형식으로
- [ ] **내부 헤더 보호**: 외부에서 `X-Client-Id` 같은 내부용 헤더를 넣어 보내면 제거

> 공개 API에서 Gateway = **"문지기 + 계량기"**. 들어올 자격이 있는지 확인하고, 얼마나 썼는지 센다.

## 4. Spring Cloud Gateway 핵심 개념

| 용어 | 의미 | 예 |
|---|---|---|
| **Route** | 요청을 어디로 보낼지 정의한 규칙 | id, uri, predicates, filters |
| **Predicate** | 이 Route에 해당하는지 판단하는 조건 | `Path=/users/**`, `Method=GET`, `Header=...` |
| **Filter** | 요청 전/응답 후에 끼워 넣는 처리 | `StripPrefix=1`, `AddRequestHeader`, 커스텀 인증 필터 |

요청 흐름:

```
요청 → Predicate 매칭 → Pre Filter들 → 대상 서비스 호출 → Post Filter들 → 응답
```

설정 예시 (application.yml):

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: data-service
          uri: http://localhost:8081      # Eureka 사용 시 lb://DATA-SERVICE
          predicates:
            - Path=/api/v1/parkings/**
          filters:
            - StripPrefix=2               # /api/v1 제거 후 전달
```

> 참고: 최신 Spring Cloud에서는 Gateway가 **WebFlux(리액티브) 버전**과 **MVC(서블릿) 버전**으로 나뉜다.
> 공부용으로는 자료가 가장 많은 WebFlux 버전으로 시작하고, 설정 prefix는 사용하는 버전 공식 문서에서 확인할 것.

## 5. 같이 알아두면 좋은 것

- **WebFlux / 리액티브** 기초: Spring Cloud Gateway(WebFlux)는 Netty 기반 논블로킹. `Mono`, `Flux` 정도는 읽을 수 있어야 커스텀 필터 작성 가능
- **BFF (Backend For Frontend)**: 웹용/앱용 Gateway를 따로 두는 패턴
- **다른 Gateway 제품**: Nginx, Kong, AWS API Gateway — 역할은 같고 구현/운영 방식이 다름

## 6. 스스로 답해보기

1. Gateway가 죽으면 전체가 죽는데(SPOF), 어떻게 대응하나?
2. 인증을 Gateway에서만 하면 내부 서비스는 인증을 안 해도 되나?
3. Gateway에 비즈니스 로직을 넣으면 왜 안 좋은가?

## 내 메모

