# 09. Gateway Rate Limit — 원리와 구현 순서

## 1. 왜 Gateway에서 제한하는가

Gateway는 외부 요청이 내부 서비스에 닿기 전 반드시 지나는 경계다. 요청 수를 여기서 제한하면
과도한 트래픽이 data-service, DB, 외부 API 연결까지 소비하기 전에 빠르게 차단할 수 있다.

```text
외부 요청
  → Gateway 인증
  → Rate Limit 판단
  → 허용된 요청만 downstream 호출
```

Rate Limit은 인증을 대체하지 않는다.

- **인증(Authentication)**: 누구의 요청인가?
- **인가(Authorization)**: 이 API를 호출할 권한이 있는가?
- **Rate Limit**: 이 호출자가 지금 더 요청할 수 있는가?

## 2. 이 프로젝트에서 제한 기준

IP 기준 제한은 같은 NAT/회사망의 정상 사용자까지 함께 차단할 수 있고, IP를 바꾸면 우회하기도 쉽다.
공개 API Gateway에서는 보통 인증된 호출 앱의 `clientId`를 기준으로 제한한다.

```text
API Key 또는 JWT 검증
  → AuthenticatedPrincipal(clientId, ...)
  → Gateway가 신뢰하는 X-Client-Id 발급
  → clientId를 Rate Limit key로 사용
```

외부가 보낸 `X-Client-Id`를 그대로 key로 쓰면 위조할 수 있다. 반드시 인증 뒤 Gateway가 발급한
값 또는 `AuthenticatedPrincipal`에서 얻은 값만 사용한다.

## 3. Token Bucket 원리

처음 구현할 알고리즘은 Token Bucket이 적절하다.

```text
Bucket 용량: 10개 token
보충 속도: 초당 5개 token
요청 1회: token 1개 소비
```

- token이 있으면 요청을 통과시킨다.
- token이 없으면 429 Too Many Requests를 반환한다.
- 시간이 지나면 설정한 보충 속도만큼 token이 다시 생긴다.
- 순간적으로 몰리는 요청은 bucket 용량만큼 허용한다(burst).

예를 들어 `replenishRate=5`, `burstCapacity=10`이면 장기적으로 초당 5회를 허용하면서,
짧은 순간에는 최대 10회까지 처리할 수 있다.

## 4. Redis가 필요한 이유

Gateway가 한 대일 때는 메모리 카운터도 가능하지만, Gateway 인스턴스를 여러 대로 늘리면 각 인스턴스의
메모리 카운터가 서로 다르다.

```text
Gateway 1 메모리: demo-client 요청 5회
Gateway 2 메모리: demo-client 요청 5회
→ 의도한 초당 5회 제한이 실제로는 10회가 될 수 있음
```

Redis를 공유 저장소로 쓰면 모든 Gateway 인스턴스가 같은 bucket 상태를 본다. Spring Cloud Gateway의
`RequestRateLimiter`도 Redis 기반 Token Bucket 구현을 제공한다.

## 5. 역할 분리

```text
developer-service
  - 앱 등록
  - clientId, API Key 해시, 플랜(FREE/PRO) 관리
  - 키 발급·폐기

Gateway
  - 인증 결과에서 clientId·플랜을 확보
  - Redis로 요청 한도 집행
  - 초과 시 429 응답
```

developer-service는 Rate Limit을 직접 세지 않는다. 정책의 근거(누구인지, 어떤 플랜인지)를 관리하고,
Gateway는 요청마다 그 정책을 집행한다.

현재 단계에서는 `LocalApiKeyAuthenticator`가 `demo-client`를 반환하므로 developer-service 없이도
Rate Limit의 동작을 먼저 구현할 수 있다. 단, 모든 유효 키가 같은 client로 취급되므로 실제 고객별
제한은 developer-service를 붙인 뒤에 가능하다.

## 6. 현재 Gateway 필터 순서

Rate Limit은 인증된 `clientId`가 필요하므로 인증 전에는 둘 수 없다.

```text
RequestSanitizationFilter (-3)
  → TraceIdFilter (-2)
  → ApiKeyAuthenticationFilter (-1): 인증 및 GatewayPassport 발급
  → Rate Limit (신뢰 가능한 clientId를 얻은 뒤, downstream 호출 전)
  → route
```

구현 방식은 두 가지가 있다.

1. Spring Cloud Gateway `RequestRateLimiter` + `KeyResolver`를 사용한다.
2. 인증 결과를 직접 이용해야 하면 custom GlobalFilter로 Redis bucket을 호출한다.

처음에는 1번으로 Token Bucket과 Redis의 동작을 익히고, 복잡한 플랜·일일 quota 요구가 생길 때만 2번을
추가한다. `clientId`를 request attribute에 보관할지, Gateway가 발급한 내부 헤더에서 읽을지도 구현 전에
정해야 한다. 외부 입력 헤더를 읽는 방식은 금지한다.

## 7. 내일 구현 순서

### Step 1. Redis와 고정 정책으로 시작

- Docker Redis를 기동한다.
- `demo-client`에 고정 정책을 적용한다.
- 예시: 초당 5회 보충, 순간 최대 10회.
- 초과하면 표준 JSON 오류 응답 `429`를 반환한다.

이 단계에서는 developer-service를 만들지 않는다. Rate Limit 알고리즘, Redis, Gateway filter 순서에만
집중한다.

### Step 2. KeyResolver 구현

- 인증이 끝난 뒤의 신뢰 가능한 `clientId`를 key로 반환한다.
- key가 없으면 익명 요청으로 통과시키지 말고 인증 흐름에서 이미 401 처리됐는지 확인한다.
- `X-Client-Id`를 쓴다면 반드시 Gateway가 발급한 이후의 값만 사용한다.

### Step 3. HTTP 통합 테스트

아래를 테스트한다.

```text
동일 clientId로 허용 횟수 안의 요청 → 200
동일 clientId로 한도 초과 요청 → 429
잠시 기다린 뒤 token 보충 → 다시 200
다른 clientId → 별도 bucket 사용
429 응답에도 X-Trace-Id 포함
429 요청은 downstream에 전달되지 않음
```

현재 `LocalApiKeyAuthenticator`는 한 client만 반환하므로, Step 3의 다른 clientId 테스트는
developer-service 연동 이후에 추가한다.

### Step 4. developer-service 연동

- API Key 해시 조회 결과에서 `clientId`, `plan`을 얻는다.
- `FREE`, `PRO` 플랜별 replenish rate와 burst capacity를 다르게 적용한다.
- API Key 폐기 시 인증 캐시와 관련 제한 정책 갱신을 확인한다.

### Step 5. 일일 Quota는 별도 기능으로 추가

초당 Rate Limit과 일일 사용량 제한은 목적이 다르다.

```text
Rate Limit: 짧은 시간 폭주 방지
Daily Quota: 플랜별 하루 총 사용량 제한
```

일일 quota는 Redis `INCR`와 자정 만료를 이용해 별도 필터/서비스로 구현한다. 처음부터 Token Bucket에
섞지 않는다.

## 8. 운영 시 확인할 지표

- client별 허용/거절(429) 횟수
- route별 429 비율
- Redis 응답 시간과 오류율
- Circuit Breaker 503 비율과 429 비율의 변화
- downstream 요청 수 감소 효과

Rate Limit은 숫자를 정해 두는 기능이 아니라, 정상 사용자가 불편하지 않으면서도 과도한 요청이 내부
서비스를 소진하지 않도록 계속 조정하는 보호 정책이다.
