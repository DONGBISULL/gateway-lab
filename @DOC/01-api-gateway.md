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

### 4-1. 자주 쓰는 Filter, 역할별 정리

**① 경로 변환** — 백엔드로 넘기기 전에 URL 형태를 바꿈

| 필터 | 역할 |
|---|---|
| `RewritePath` | 요청 URL 경로 변경 (정규식 기반, 완전 재작성) |
| `StripPrefix` | URL 앞쪽 경로 일부 제거 (segment 개수 기준) |

**② 인증/인가** — 요청이 통과할 자격이 있는지 검증

| 필터 | 역할 |
|---|---|
| `AuthenticationFilter` | JWT 등 인증 토큰 검증 (커스텀 `GlobalFilter`로 직접 구현, Phase 2·3) |

**③ 헤더 관리** — 내부 서비스로 넘어가는 요청/응답의 헤더 조정

| 필터 | 역할 |
|---|---|
| `AddRequestHeader` | 내부 서비스에 전달할 요청 헤더 추가 (예: `X-Client-Id`) |
| `RemoveRequestHeader` | 민감하거나 불필요한 헤더 제거 (예: 외부에서 위조해 보낸 `X-Client-Id`) |

**④ 트래픽 보호/안정성** — 부하·장애로부터 시스템 보호

| 필터 | 역할 |
|---|---|
| `RequestRateLimiter` | Redis 기반 요청 횟수 제한 (Phase 4) |
| `CircuitBreaker` | 대상 서비스 장애 시 요청 차단 또는 fallback 처리 |

> `data-service`용 라우트에는 `RewritePath`와 Circuit Breaker fallback이 적용돼 있다. 내부 헤더 보호는 `RequestSanitizationFilter`, 인증은 `ApiKeyAuthenticationFilter`, Passport 전파는 `GatewayPassportIssuer`가 각각 GlobalFilter/협력 객체로 나눠 처리한다. Rate Limit은 로드맵 Phase 4에서 추가한다.

### 4-2. 현재 구현된 Gateway 공통 처리

`RequestSanitizationFilter`가 필터 체인에서 가장 먼저 돈다(order -3). `sanitize/HeaderRequestSanitizer`에게 위임해 `X-Client-Id`, `X-Gateway-Passport`, `X-Trace-Id`처럼 Gateway만 발급해야 하는 헤더를 클라이언트가 보낸 값째로 제거한다. 뒤에 오는 필터들은 이 단계를 거친 요청이 이미 정제됐다고 가정하고 동작한다. 상세 근거는 [07-request-sanitize.md](07-request-sanitize.md) 참고.

`ApiKeyAuthenticationFilter`는 `X-API-Key`를 검증하는 문지기 역할만 한다. 실패하면 Gateway에서 표준 401 JSON을 반환해 downstream 서비스까지 요청이 가지 않는다. 성공하면 `passport/GatewayPassportIssuer`에게 넘겨서 `X-API-Key`를 제거하고(더 이상 downstream에 전달하지 않음) `X-Client-Id`, `X-Gateway-Passport`에 Gateway가 발급한 값을 설정한다(두 헤더의 클라이언트 원본 값은 앞선 `RequestSanitizationFilter`에서 이미 제거됨). 호출자 `clientId`는 설정 파일의 전역값이 아니라 API Key 검증 결과에서 얻는다. 현재 단일 키 실습에서는 `demo-client`를 반환하며, 다음 단계에서 Redis 캐시와 developer-service 조회 결과로 교체한다.

`X-Gateway-Passport`는 외부 API 응답이 아니라 Gateway와 내부 서비스 사이의 요청 헤더다. `passport/GatewayPassport` 모델을 URL-safe Base64 JSON으로 직렬화해 전파한다. 현재 API Key 단계에서는 아래처럼 호출 앱 정보만 담는다. 사용자 로그인/JWT 단계에서는 검증된 JWT claim에서 `userId`, 권한, 디바이스 정보를 추가한다.

```json
{
  "clientId": "demo-client",
  "authenticationType": "API_KEY",
  "issuedAt": "2026-09-27T00:00:00Z",
  "traceId": "..."
}
```

`TraceIdFilter`는 요청마다 `X-Trace-Id`를 새로 발급해 요청·응답·내부 서비스 호출에 전파하고, 메서드·경로·상태·처리 시간을 로그로 남긴다.

현재 요청 처리 순서는 다음과 같다.

```text
외부 요청
  → RequestSanitizationFilter (-3): 외부가 주입한 내부 전용 헤더 제거
  → TraceIdFilter (-2): Gateway가 발급한 trace ID 추가
  → ApiKeyAuthenticationFilter (-1): API Key 검증
  → GatewayPassportIssuer: API Key 제거, client ID·Passport 발급
  → Route / Circuit Breaker / downstream
```

`GatewayExceptionHandler`의 `@Order(-2)`는 위 GlobalFilter 순서와 별개의 예외 핸들러 체인 순서다. `GatewayException`은 표준 JSON 오류 응답으로 바꾸고, 처리하지 않는 예외는 Spring Boot 기본 예외 핸들러에 넘긴다.

### 4-3. 인증 방식 확장 순서

현재는 **Phase 2 API Key 인증** 단계다. `LocalApiKeyAuthenticator`는 실습용 단일 키 검증기이며, 다음에는 developer-service의 앱/API Key 조회와 Redis 캐시로 교체한다. 원문 API Key는 검증에만 사용하며 `AuthenticatedPrincipal`, 로그, `GatewayPassport`에 저장하거나 전달하지 않는다.

OAuth2 Client Credentials + JWT는 API Key 흐름과 테스트가 완결된 뒤 **Phase 3**에 추가한다. 이때 역할은 다음처럼 나눈다.

```text
외부 호출 앱 → auth-server: client_credentials로 JWT access token 발급 요청
외부 호출 앱 → Gateway: Authorization: Bearer <JWT>
Gateway(Resource Server): JWKS로 JWT 서명·만료·issuer·audience·scope 검증
Gateway → downstream: 검증 결과를 GatewayPassport로 전달
```

Gateway가 외부 호출자에게 JWT를 발급하지 않는다. 토큰 발급은 auth-server, Gateway는 Resource Server로서 토큰 검증과 scope 인가를 담당한다. `GatewayPassport`는 외부 JWT를 그대로 전달하는 용도가 아니라, Gateway가 검증한 호출자 컨텍스트를 내부 서비스에 전달하기 위한 별도 내부 헤더다.

## 5. 같이 알아두면 좋은 것

- **WebFlux / 리액티브** 기초: Spring Cloud Gateway(WebFlux)는 Netty 기반 논블로킹. `Mono`, `Flux` 정도는 읽을 수 있어야 커스텀 필터 작성 가능
- **BFF (Backend For Frontend)**: 웹용/앱용 Gateway를 따로 두는 패턴
- **다른 Gateway 제품**: Nginx, Kong, AWS API Gateway — 역할은 같고 구현/운영 방식이 다름

## 6. 스스로 답해보기

1. Gateway가 죽으면 전체가 죽는데(SPOF), 어떻게 대응하나?
2. 인증을 Gateway에서만 하면 내부 서비스는 인증을 안 해도 되나?
3. Gateway에 비즈니스 로직을 넣으면 왜 안 좋은가?

## 내 메모
