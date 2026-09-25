# 03. 공개 API 토큰 인증

외부 개발자에게 API를 공개할 때 "누가 호출하는지 확인하고, 허용된 만큼만 쓰게 하는 것"이 핵심이다.

## 1. 먼저 구분할 것: 누가 호출하나?

| 호출 주체 | 예 | 적합한 방식 |
|---|---|---|
| **외부 개발자의 서버(앱)** 가 공개 데이터를 조회 | 공공데이터 조회, 날씨 API | API Key 또는 **OAuth2 Client Credentials** |
| 외부 앱이 **우리 서비스 사용자의 개인 데이터**에 접근 | "카카오로 로그인" 후 내 정보 조회 | **OAuth2 Authorization Code + PKCE** |
| 우리 자체 웹/앱의 로그인 사용자 | 우리 서비스 프론트엔드 | 세션 또는 로그인 JWT |

→ 이 실습의 중심은 **첫 번째(Client Credentials)**, 두 번째는 심화 과제.

## 2. 방식 비교

### 2-1. API Key (가장 단순)

```
GET /api/v1/parkings?serviceKey=abc123...        (공공데이터포털 방식)
GET /api/v1/parkings   X-API-Key: abc123...     (헤더 방식, 더 권장)
```

- 발급: 개발자 포털에서 키 하나 받음
- 검증: Gateway가 매 요청마다 키를 조회 (DB → Redis 캐시)
- 장점: 쉽다, 개발자가 쓰기 편하다
- 단점: 키가 곧 비밀번호라 **유출되면 폐기 전까지 계속 사용 가능**, 만료 없음, 권한 표현이 약함
- 주의: 쿼리스트링에 넣으면 서버 로그/브라우저 기록에 남음 → 헤더 권장
- 저장: DB에는 **키 원문 대신 해시**로 저장 (비밀번호처럼)

### 2-2. OAuth2 Client Credentials + JWT (표준 방식)

```
① POST /oauth2/token
   Authorization: Basic base64(client_id:client_secret)
   grant_type=client_credentials&scope=data.read

   ← { "access_token": "eyJ...", "token_type": "Bearer",
       "expires_in": 3600, "scope": "data.read" }

② GET /api/v1/parkings
   Authorization: Bearer eyJ...
```

- client_secret 은 **토큰 받을 때만** 사용, 실제 API 호출은 짧은 수명의 토큰으로
- 토큰이 유출돼도 **만료 시간(예: 1시간)** 이 지나면 무효
- scope 로 권한을 세밀하게 나눔 (`data.read`, `data.write`, `stats.read`)

### 2-3. 비교표

| 항목 | API Key | OAuth2 + JWT |
|---|---|---|
| 구현 난이도 | 낮음 | 중간 |
| 만료 | 없음 (수동 폐기) | 있음 (짧게) |
| 권한(scope) | 키에 매핑해서 별도 관리 | 토큰 안에 포함 |
| Gateway 검증 | 매번 저장소 조회 | **서명만 검증** (저장소 조회 없이 가능) |
| 즉시 폐기 | 쉬움 (키 삭제) | 어려움 (만료까지 유효) → 대책 필요 |
| 표준 | 없음 (회사마다 다름) | RFC 6749 등 표준 |

> 실무에서는 **둘 다 제공**하는 경우도 많다. 이 실습은 API Key → OAuth2 순서로 둘 다 만들어 보고 비교한다.

## 3. JWT 공부 포인트

- [ ] 구조: `Header.Payload.Signature` (Base64URL), **암호화가 아니라 서명** → payload에 민감정보 넣지 말 것
- [ ] 주요 claim: `iss`(발급자), `sub`(client_id), `aud`(대상 API), `exp`(만료), `iat`, `scope`, `jti`(토큰 ID)
- [ ] 서명 알고리즘: HS256(공유 비밀키) vs **RS256(개인키 서명 / 공개키 검증)** → 서비스가 여럿이면 RS256
- [ ] **JWKS**: 인증 서버가 공개키를 `/oauth2/jwks` 로 공개 → Gateway가 받아서 캐시하고 검증
- [ ] `kid` 헤더: 여러 키 중 어떤 키로 서명했는지 → **키 회전(rotation)** 가능하게 함

## 4. Gateway에서의 인증/인가 흐름

```
요청 도착
 │
 ├─ 토큰 없음 ─────────────────────────────▶ 401 Unauthorized
 ├─ 서명 불일치 / 만료 / iss·aud 틀림 ────────▶ 401 Unauthorized
 ├─ scope 부족 (data.read 없는데 조회) ─────▶ 403 Forbidden
 ├─ 폐기된 client (블랙리스트) ─────────────▶ 401
 ├─ Rate Limit 초과 ───────────────────────▶ 429 Too Many Requests
 └─ 통과 → X-Client-Id, X-Scopes 헤더 추가 → 하위 서비스로 전달
```

- 내부 서비스는 토큰을 다시 검증하지 않고 헤더만 신뢰 **(단, 외부에서 이 헤더를 직접 넣어 보내면 Gateway가 반드시 지워야 함)**
- 더 엄격하게 하려면 내부 서비스도 JWT를 재검증 (Zero Trust) → 트레이드오프 공부

## 5. 공개 API라서 추가로 고민할 것

- [ ] **토큰 즉시 폐기**: JWT는 만료 전까지 유효 → ① 만료를 짧게 ② Redis에 폐기된 client_id/jti 블랙리스트 ③ Introspection(`/oauth2/introspect`)으로 매번 조회 — 각각의 장단점
- [ ] **client_secret 재발급/회전**: 기존 secret과 새 secret을 잠깐 동시에 허용
- [ ] **요금제(Plan)별 제한**: Free = 초당 5회·일 1,000회, Pro = 초당 50회·일 100,000회
- [ ] **응답 헤더로 남은 호출량 안내**: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After`
- [ ] **표준 에러 형식**: 모든 서비스가 같은 JSON 에러 (예: RFC 9457 Problem Details)
- [ ] **API 버저닝**: `/api/v1/...` → 호환 안 되게 바뀌면 `/v2`, 구버전 지원 종료 안내
- [ ] **HTTPS 필수**, 로그에 토큰/키 원문 남기지 않기(마스킹)
- [ ] **호출 이력(감사 로그)**: 누가·언제·무엇을 호출했는지 → usage-service

## 6. 스스로 답해보기

1. API Key를 Gateway가 매번 DB에서 조회하면 어떤 문제가 생기나? 어떻게 줄이나?
2. JWT를 쓰면서 "지금 당장 이 개발자 차단"은 어떻게 구현하나?
3. HS256으로 서명하면 Gateway와 인증 서버가 같은 비밀키를 가져야 하는데, 뭐가 문제인가?
4. 401과 403의 차이는?

## 내 메모

