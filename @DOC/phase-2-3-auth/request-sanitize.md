# 07. Request Sanitize 개발 순서

> 참고: [토스 Gateway 아키텍처](https://toss.tech/article/22910)

## 1. 토스 Gateway 파이프라인 순서

원문이 제시하는 처리 순서와, 그 순서로 배치한 이유:

| 순서 | 단계 | 하는 일 | 왜 이 순서인가 |
|---|---|---|---|
| 1 | **Sanitize** | 클라이언트가 보낸 값이 올바르지 않으면 지우거나 올바른 값으로 치환 | 이후 모든 단계가 이 요청값을 신뢰하고 쓰기 때문에 가장 먼저 정리해야 함 |
| 2 | **Passport 생성** | 인증 서버에서 유저/디바이스 정보를 받아 downstream에 전파할 토큰 생성 | 정제된 요청이어야 여기 담을 유저/디바이스 정보도 신뢰할 수 있음 |
| 3 | **종단간 암호화 해제** | 앱이 암호화해 보낸 요청 바디를 Gateway가 복호화 | 복호화에 필요한 키 컨텍스트가 Passport 단계 정보에 의존 |
| 4 | **Dynamic Security** | 앱만 알 수 있는 값으로 요청에 서명한 값 검증 → 위변조/재전송(replay) 방지 | 복호화된 실제 데이터를 대상으로 정합성을 검증해야 하므로 암호화 해제 다음 |
| 5 | **Circuit Breaker** | 다운스트림 서비스 장애 시 빠르게 실패 처리 | 모든 검증을 통과한 요청만 실제로 다운스트림에 호출되므로 마지막 |

핵심은 **"신뢰할 수 없는 입력값을 다루는 단계일수록 앞에 둔다"** 는 것. Sanitize가 1번인 이유도 여기 있다 — 뒤에 오는 Passport/암호화/서명 검증 전부가 "요청값이 이미 올바른 형태"라고 가정하고 동작한다.

## 2. 우리 프로젝트에 매핑

| 토스 단계 | gateway-lab 현황 | 비고 |
|---|---|---|
| Sanitize | ❌ 아직 없음 — 이번에 개발 | `ApiKeyFilter`가 내부 헤더(`X-Client-Id`, `X-Gateway-Passport`) 제거를 인라인으로 하고 있는데, 이 책임을 Sanitize 단계로 옮길지 검토 |
| Passport 생성 | ✅ 구현됨 (`passport/GatewayPassport`, `GatewayPassportCodec`) | 로드맵보다 앞서 있음. 단, 지금은 "인증 서버 조회"가 아니라 API Key 검증 결과에서 바로 만듦 (Phase 2 범위) |
| 종단간 암호화 해제 | ⏭ 스코프 아웃 | 공개 데이터 API 실습이라 앱-서버 간 대칭키 교환/암호화 채널이 필요 없음. 은행 앱처럼 클라이언트가 신뢰 안 되는 환경(루팅/변조 앱)을 가정할 때만 의미 있음 |
| Dynamic Security | ⏭ 스코프 아웃 (심화로 미룸) | replay/위변조 방지는 로드맵 Phase 7 "심화" 성격 — 지금은 OAuth2/JWT(Phase 3)로 인증만 표준화하는 게 우선 |
| Circuit Breaker | 로드맵 Phase 5에 예정 | 이미 계획에 있음, 지금 할 일 아님 |

즉 지금 당장 할 일은 **Sanitize** 하나뿐이고, 나머지는 이미 하고 있거나(Passport) 이번 실습 범위 밖(암호화/서명, 스코프는 06-production-design.md 참고).

## 3. Sanitize 개발 순서 (실제로 할 일)

### 책임 분리

요청 방어 규칙을 모두 `RequestSanitizer`에 넣지 않는다. 규칙의 성격에 따라 적용 위치를 나눈다.

| 항목 | 적용 위치 | 현재 단계 |
|---|---|---|
| `X-Client-Id`, `X-Gateway-Passport`, `X-Trace-Id` 제거 | `RequestSanitizer` | 적용 |
| `X-API-Key` 제거 | 인증 성공 후 `GatewayPassportIssuer` | 적용 |
| 헤더 최대 길이 | 서버/Netty 설정 | 배포 요구가 정해진 뒤 적용 |
| `Host`, `X-Forwarded-*` 신뢰 정책 | 프록시·Load Balancer 설정 | 프록시 도입 시 적용 |
| 쿼리 파라미터 길이·형식 | 각 API의 요청 검증 | API별 요구가 생길 때 적용 |
| 허용 path 범위 | Gateway Route Predicate | 현재 라우트로 적용 |
| 비정상 문자·형식 | HTTP 서버 기본 파서 | 별도 규칙 필요 시만 추가 |
| 내부 헤더 제거 검증 | Sanitizer 단위 테스트 + Gateway 통합 테스트 | 다음 작업 |

### 현재 헤더 처리 흐름

```text
외부 요청
  → RequestSanitizationFilter: 내부 전용 헤더 제거
  → TraceIdFilter: Gateway 발급 Trace ID 추가
  → ApiKeyAuthenticationFilter: API Key 검증
  → GatewayPassportIssuer: API Key 제거, Client ID·Passport 추가
  → downstream
```

`X-API-Key`는 인증에 필요하므로 Sanitize 단계에서 유지한다. 반대로 `X-Client-Id`,
`X-Gateway-Passport`, `X-Trace-Id`는 외부 값이 신뢰 경계를 넘지 않도록 먼저 제거한 뒤,
Gateway가 발급한 값만 다시 넣는다.

1. **범위 정하기** — 지금 실제로 뭘 sanitize해야 하는지 구체화
   - 내부 전용 헤더 위조 방지: `X-Client-Id`, `X-Gateway-Passport`, `X-Trace-Id` (현재 `ApiKeyAuthenticationFilter`/`TraceIdFilter`에 흩어져 있음)
   - 잘못된/과도한 값: 헤더 길이 제한, 허용되지 않은 문자(개행 등 헤더 인젝션 문자) 제거
   - 그 외(쿼리 파라미터, path traversal 등)는 필요해지면 추가 — 지금 안 쓰는 규칙을 미리 만들지 않기
2. **계약 정의** — `authentication/ApiKeyAuthenticator` 패턴과 동일하게 `sanitize/RequestSanitizer` 인터페이스 하나 (동기 메서드, I/O 없음)
3. **기본 구현체 작성** — 1번에서 정한 규칙만 담은 구현체 하나로 시작 (규칙별 클래스 분리는 규칙이 늘어난 뒤에)
4. **필터로 연결** — `filter/RequestSanitizationFilter` (`GlobalFilter`), 지금 필터들보다 앞선 순서로 등록
   - 현재 순서: `TraceIdFilter`(-2) → `ApiKeyAuthenticationFilter`(-1)
   - Sanitize는 이보다 먼저 돌아야 하므로 `-3`
5. **기존 로직 이전 검토** — `passport/GatewayPassportIssuer`가 하던 내부 헤더 제거(위조 헤더 삭제)를 Sanitize 필터로 옮길지 결정 (책임 중복 방지). Passport 발급(새 헤더 설정)은 그대로 `GatewayPassportIssuer`에 남긴다.
6. **테스트** — 정상 요청 / 내부 헤더 위조 요청 / 허용 안 되는 문자 포함 요청, 각각 downstream에 어떤 값이 전달되는지 확인
7. **문서 갱신** — `01-api-gateway.md` 4-2절("현재 구현된 Gateway 공통 처리")에 Sanitize 단계 추가

## 내 메모
