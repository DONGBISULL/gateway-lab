# 04. 실습 구조 설계

## 1. 시나리오

**"공영주차장 정보 Open API"** 를 외부 개발자에게 공개한다고 가정한다.
(도메인은 단순하게 두고 인증·Gateway·MSA 구조에 집중)

- 외부 개발자는 가입 후 앱을 등록하고 `client_id / client_secret` (또는 API Key)을 받는다.
- 토큰을 발급받아 주차장 조회 API를 호출한다.
- 요금제에 따라 호출량이 제한되고, 개발자는 자기 사용량을 조회할 수 있다.

## 2. 구성 요소

| 모듈 | 포트 | 역할 | 자기 DB |
|---|---|---|---|
| gateway | 8080 | 단일 진입점, 토큰 검증, scope 인가, Rate Limit, 라우팅, 호출 로그 발행 | 없음 (Redis 사용) |
| auth-server | 9000 | OAuth2 토큰 발급, JWKS 공개 (Spring Authorization Server) | client 정보 |
| developer-service | 8084 | 개발자 가입, 앱 등록, client/API Key 발급·폐기, 요금제 | 개발자·앱·플랜 |
| data-service | 8081 | 공개 API 본체 (주차장 조회) | 주차장 데이터 |
| usage-service | 8085 | 호출 이력 저장, 일별 통계 | 호출 로그 |
| discovery | 8761 | Eureka Server | - |
| redis | 6379 | Rate Limit 카운터, API Key 캐시, 폐기 블랙리스트 | - |

## 3. 외부 공개 API (Gateway 기준 주소)

| Method | 경로 | 전달 대상 | 인증 | 필요 scope |
|---|---|---|---|---|
| POST | `/oauth2/token` | auth-server | client_id/secret (Basic) | - |
| GET | `/oauth2/jwks` | auth-server | 없음 | - |
| GET | `/api/v1/parkings` | data-service | Bearer | `data.read` |
| GET | `/api/v1/parkings/{id}` | data-service | Bearer | `data.read` |
| GET | `/api/v1/usage/me` | usage-service | Bearer | `stats.read` |

개발자 포털용 API (개발자 본인 로그인 필요):

| Method | 경로 | 전달 대상 | 설명 |
|---|---|---|---|
| POST | `/portal/developers` | developer-service | 개발자 가입 |
| POST | `/portal/apps` | developer-service | 앱 등록 → client_id/secret 발급 |
| POST | `/portal/apps/{id}/rotate-secret` | developer-service | secret 재발급 |
| DELETE | `/portal/apps/{id}` | developer-service | 앱 삭제 → 즉시 차단 |

> 포털 로그인은 처음엔 단순 로그인 JWT로 시작하고, 심화 단계에서 Authorization Code 방식으로 바꿔본다.

## 4. 핵심 흐름

### 4-1. 앱 등록 (서비스 간 통신 연습)

```
개발자 ─▶ Gateway ─▶ developer-service
                        │ 1. 앱 정보 저장 (plan=FREE)
                        │ 2. auth-server 내부 API 호출 → client 등록 (scope: data.read)
                        │ 3. client_secret 은 이때 한 번만 보여주고 해시로 저장
                        ◀── client_id / client_secret 응답
```

### 4-2. 토큰 발급 + API 호출

```
외부 앱 ─ POST /oauth2/token ─▶ Gateway ─▶ auth-server
        ◀── access_token (JWT, RS256, exp 1h, sub=client_id, scope=data.read, plan=FREE)

외부 앱 ─ GET /api/v1/parkings (Bearer) ─▶ Gateway
   Gateway:
     ① JWKS 공개키로 서명·만료·iss·aud 검증   (공개키는 캐시, 매 요청 조회 X)
     ② 블랙리스트 확인 (Redis: 폐기된 client_id)
     ③ scope 확인: data.read
     ④ Rate Limit: key = client_id, 한도 = plan 에 따라
     ⑤ 외부에서 들어온 X-Client-Id 헤더 제거 후 새로 세팅
     ⑥ lb://DATA-SERVICE 로 전달
     ⑦ 응답 후 호출 이벤트 발행 → usage-service (비동기)
```

### 4-3. 앱 삭제 → 즉시 차단

```
developer-service: 앱 삭제
  ├─▶ auth-server: client 삭제 (새 토큰 발급 막기)
  └─▶ Redis 블랙리스트에 client_id 추가 (TTL = 토큰 최대 수명)
       → 이미 발급된 토큰도 Gateway에서 바로 차단
```

### 4-4. 장애 시나리오

```
usage-service 중지   → API 호출은 정상이어야 함 (로그는 비동기, 실패해도 본 요청에 영향 X)
data-service 지연    → Gateway Circuit Breaker → 503 + 표준 에러
auth-server 중지     → 새 토큰 발급은 불가, 이미 받은 토큰은 계속 사용 가능 (JWKS 캐시 덕분)
```

→ 이 세 가지를 직접 실험해 보는 것이 MSA 공부의 핵심.

## 5. 설계 원칙

- 외부 요청은 **반드시 Gateway를 통해서만** 들어온다. 내부 서비스 포트는 외부에 열지 않는다.
- 인증·인가·Rate Limit은 **Gateway 한 곳**에서, 비즈니스 로직은 **서비스**에서.
- 서비스끼리 **DB를 공유하지 않는다** (처음엔 각자 H2로 시작).
- secret/API Key는 **해시로 저장**, 로그에는 마스킹.
- 모든 에러는 **같은 형식**의 JSON으로 응답.

## 6. 표준 에러 응답 예시

```json
{
  "type": "https://api.example.com/errors/rate-limit",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "FREE 플랜은 초당 5회까지 호출할 수 있습니다.",
  "instance": "/api/v1/parkings",
  "traceId": "4bf92f3577b34da6"
}
```

## 내 메모

