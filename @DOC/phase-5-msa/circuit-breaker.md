# Circuit Breaker — 원리와 검증 방법

## 1. 왜 필요한가

data-service가 느려지면 Gateway는 응답을 기다리며 연결과 요청을 계속 쌓는다.
그러면 data-service만 죽는 게 아니라 Gateway 전체가 느려져서 장애가 번진다.

Circuit Breaker는 실패율이 임계치를 넘으면 호출 자체를 막고 바로 fallback(503)을 준다.

- 클라이언트는 타임아웃을 기다리지 않고 빠르게 실패 응답을 받는다.
- 죽어가는 서비스는 요청이 멈춰 회복할 시간을 얻는다.

Circuit Breaker는 호출하는 대상이 아니라 **모든 호출이 지나가는 문(게이트)** 이다.

## 2. 상태 3개

전기 회로 차단기와 같은 이름이라 헷갈리기 쉽다. **CLOSED가 정상, OPEN이 차단 중**이다.

```text
CLOSED ──(최근 N회 중 실패율 ≥ 임계치)──▶ OPEN ──(wait-duration 경과 후 다음 호출)──▶ HALF_OPEN
  ▲                                       (호출 안 하고 즉시 fallback)                  │
  └────────────(시험 호출 M번 성공)────────────────────────────────────────────────────┘
                                          (시험 호출이 실패하면 다시 OPEN)
```

| 상태 | 의미 | 서비스 관점 |
|---|---|---|
| CLOSED | 요청이 downstream까지 그대로 통과 | 정상 |
| OPEN | 실패율이 임계치를 넘어서 차단 중, 즉시 fallback | downstream 이상, 빠르게 실패 중 |
| HALF_OPEN | 시험 호출 몇 번만 내보내 회복 확인 | 복구 시도 중 |

OPEN은 Circuit Breaker가 고장 났다는 뜻이 아니라 **차단 장치가 일을 했다**는 뜻이다.
다만 그 상황 자체는 downstream 문제의 신호라서 알림 대상이다.

## 3. 설정값의 의미

`failure-rate-threshold: 50`은 "호출량이 50%"가 아니라 **최근 호출 중 실패한 비율**이다.

| 설정 (`application.yml`) | 의미 |
|---|---|
| `sliding-window-size: 10` | 최근 10번 호출만 본다 |
| `failure-rate-threshold: 50` | 그중 50% 이상 실패하면 OPEN |
| `wait-duration-in-open-state: 10s` | 10초 동안 막은 뒤 HALF_OPEN으로 넘어갈 수 있다 |
| `permitted-number-of-calls-in-half-open-state: 3` | HALF_OPEN에서 시험 호출을 3번 허용한다 |
| `timelimiter timeout-duration: 2s` | 2초 넘게 걸리면 실패로 센다 |

- 호출 횟수가 윈도우 크기만큼 쌓이기 전에는 판단하지 않는다(`minimum-number-of-calls`). 현재 설정에서 정확히 몇 번째 호출에 OPEN되는지는 테스트로 확인한다.
- 윈도우 10과 50%는 실습용 값이다. 실무에서는 실제 트래픽과 지연 분포로 정한다.

## 4. Gateway에서 "실패"로 세어지는 것

- 예외(연결 거부, 타임아웃)는 실패로 센다.
- downstream이 준 HTTP 500은 `statusCodes`를 지정하지 않으면 실패로 세지 않는 것으로 알고 있다. **테스트로 확인 필요.**
- 404/400은 정상 업무 응답이므로 실패로 세면 안 된다.
- `statusCodes: 500,502,503,504`를 지정하면 500도 실패로 세지만, downstream의 500 본문이 fallback 503으로 가려진다. 정책 결정 사항이다.

## 5. 어디에 붙이는가

Circuit Breaker는 API 서비스라서 필요한 게 아니라 **다른 서비스를 동기 호출하는 지점**에 필요하다.

| 호출 | 방식 | Circuit Breaker | 이유 |
|---|---|---|---|
| Gateway → data-service | 동기, 요청 경로 | 필요 (적용됨) | 느려지면 Gateway가 막힌다 |
| Gateway → developer-service (`/portal/**`) | 동기, 요청 경로 | 필요 | 라우트별로 회로를 분리 |
| Gateway → auth-server (`/oauth2/token`) | 동기, 요청 경로 | 필요 | 토큰 발급 프록시도 같은 구조 |
| developer-service → auth-server (client 등록) | 동기, 서비스 간 | 필요 | Gateway를 지나지 않으므로 호출하는 서비스 코드에 직접 적용 |
| Gateway → usage-service (호출 이벤트) | 비동기 | 불필요 | 큐/스트림에 넣는 구조라 본 요청이 안 막힘 |
| Gateway → auth-server (JWKS 조회) | 동기지만 캐시 | 사실상 불필요 | 매 요청 호출이 아님 |
| Gateway → Redis | 동기 | CB 대신 타임아웃 + fail-open/closed 정책 | 보안 vs 가용성 결정 |
| 각 서비스 → 자기 DB | 동기 | 불필요 | 커넥션 풀과 타임아웃으로 보호 |

서비스가 늘어나도 Gateway 라우트에는 `CircuitBreaker` 필터를 이름만 다르게 붙이고 공통 설정은 `configs.default`를 상속한다.
진짜 비용은 서비스별로 "죽으면 어떤 응답을 줄지"를 정하는 일이다.

일반적인 보호 장치의 우선순위는 **타임아웃 → 재시도(멱등 요청만) → 벌크헤드 → Circuit Breaker** 순이다.
타임아웃 없는 Circuit Breaker는 "언제 실패로 칠지"를 모르니 의미가 없다.

## 6. 검증 계획 (현재 상태: 미검증)

현재 `GatewayHttpIntegrationTest`가 증명하는 것은 "연결 거부 시 503 fallback"뿐이다.
이 테스트는 `routes[0]`을 통째로 덮어쓰므로 `application.yml`의 실제 라우트(`parkingsCB`)는 실행되지 않는다.

| 확인할 것 | 방법 |
|---|---|
| 실제 라우트 설정이 적용되는가 | `uri`를 `${app.routes.data-service-uri:...}`로 외부화해 테스트에서 URI만 교체 |
| `configs.default`가 `parkingsCB`에 먹는가 | 1.5초 지연 → 200(2초 적용), 3초 지연 → 약 2초 후 503 |
| 실패가 쌓이면 OPEN 되는가 | 타임아웃 단축(300ms) 후 10번 실패, 상태가 OPEN인지 확인 |
| OPEN 중 downstream을 호출하지 않는가 | stub 서버의 호출 횟수가 늘지 않는지 확인 (503만 보면 에러 핸들러와 구분 불가) |
| 복구되면 CLOSED로 돌아오는가 | wait-duration 단축(1s) 후 시험 호출 3번 성공 → CLOSED |
| HALF_OPEN에서 실패하면 다시 OPEN인가 | 시험 호출 실패 확인 |
| downstream 500이 회로에 영향을 주는가 | 500을 반복해서 주고 상태 확인 → `statusCodes` 결정 |

테스트 구성 요령:

- 가짜 downstream은 reactor-netty `HttpServer`를 테스트 안에서 띄운다(새 의존성 없음). 지연, 상태 코드, 호출 횟수를 제어한다.
- 스프링 컨텍스트가 테스트 간에 공유되므로 `@BeforeEach`에서 `circuitBreaker.reset()`으로 상태를 초기화한다.
- 설정값 검증(실제 yml 값)과 상태 전이 검증(시간 단축 오버라이드)은 테스트 클래스를 나눈다.

## 7. 설계 원칙

- fallback은 가짜 200이 아니라 503 + 표준 에러로 정직하게 응답한다.
- 회로는 downstream 하나당 하나로 분리한다 (`parkingsCB`처럼).
- 회로 상태를 관측한다. OPEN이 됐는데 아무도 모르면 장애 인지가 늦어진다 (Prometheus 메트릭 → Phase 6 대시보드).

## 8. 스스로 답해보기

1. 타임아웃 없이 Circuit Breaker만 있으면 왜 의미가 없는가?
2. data-service가 500을 계속 줄 때 회로가 안 열리면 어떤 문제가 생기는가?
3. usage-service 호출에는 왜 Circuit Breaker가 필요 없는가?
4. Redis가 죽으면 Rate Limit을 풀어줄 것인가(fail-open), 다 막을 것인가(fail-closed)?

## 내 메모
