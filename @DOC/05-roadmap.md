# 05. 실습 로드맵

각 Phase는 **"동작 확인"** 을 통과하면 완료로 본다.
흐름: **Gateway 기본 → API Key → OAuth2/JWT → 호출량 제한 → MSA로 나누기 → 운영**

---

## Phase 0. 준비

- [x] JDK 21 설치 확인 (`java -version`)
- [ ] Docker Desktop 설치 (Redis 띄우기용)
- [ ] Postman 또는 curl / httpie 준비
- [x] 모듈 뼈대 생성 (Spring Boot 4.1.1 + Spring Cloud 2025.1.3, Maven) — 기능 구현 전

---

## Phase 1. Gateway 라우팅 + 공개 API 본체

**목표**: Gateway가 요청을 서비스로 넘겨주는 구조 확인

- [ ] `data-service`: `GET /parkings`, `GET /parkings/{id}` (가짜 데이터)
- [ ] `gateway`: `/api/v1/parkings/**` → `http://localhost:8081` 라우트, 경로 재작성
- [ ] 공통 에러 응답 형식 정의 (404 등)

**동작 확인**
```bash
curl localhost:8080/api/v1/parkings/1
```

**공부**: Route / Predicate / Filter, API 버저닝

---

## Phase 2. API Key 인증 (공공데이터포털 방식)

**목표**: 가장 단순한 공개 API 인증을 Gateway 필터로 직접 구현

- [ ] `developer-service` 최소 버전: 앱 등록 → API Key 발급 (DB엔 해시로 저장)
- [ ] Gateway **커스텀 GlobalFilter**: `X-API-Key` 헤더 검증
  - [ ] 없음/틀림 → 401 (표준 에러)
  - [ ] 정상 → `X-Client-Id` 헤더 세팅 (외부에서 온 같은 헤더는 제거)
- [ ] 키 조회 결과를 Redis에 캐시 → 매 요청 DB 조회 제거
- [ ] 키 폐기 시 캐시도 삭제

**동작 확인**
```bash
curl localhost:8080/api/v1/parkings                          # 401
curl -H "X-API-Key: <발급키>" localhost:8080/api/v1/parkings  # 200
```

**공부**: Gateway 필터 작성, WebFlux 기초(`Mono`), 키 해시 저장, 캐시 무효화

---

## Phase 3. OAuth2 Client Credentials + JWT

**목표**: 표준 토큰 방식으로 전환 (이 프로젝트의 핵심)

- [ ] `auth-server` (Spring Authorization Server) 구성
  - [ ] 테스트 client 1개 등록 (client_credentials, scope `data.read`)
  - [ ] RS256 서명키, `/oauth2/jwks` 확인
  - [ ] 토큰에 커스텀 claim `plan` 추가
- [ ] `gateway`를 **OAuth2 Resource Server** 로 설정 (`jwk-set-uri` 로 공개키 가져오기)
- [ ] 경로별 scope 인가: `/api/v1/parkings/**` → `SCOPE_data.read`
- [ ] 401 / 403 을 표준 에러 형식으로
- [ ] jwt.io 에서 토큰 열어보고 claim 확인

**동작 확인**
```bash
TOKEN=$(curl -s -u client:secret -d "grant_type=client_credentials&scope=data.read" \
  localhost:8080/oauth2/token | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/parkings   # 200
curl -H "Authorization: Bearer 이상한토큰" localhost:8080/api/v1/parkings # 401
# data.read 없는 client로 → 403
```
- auth-server 를 끈 상태에서도 기존 토큰으로 호출되는지 확인 (JWKS 캐시)

**공부**: OAuth2 흐름, JWT 구조, RS256/JWKS, scope, 401 vs 403

---

## Phase 4. client별 Rate Limit / Quota

**목표**: 공개 API의 필수 기능 — 요금제별 호출 제한

- [ ] Redis 기동, `RequestRateLimiter` 필터 적용
- [ ] `KeyResolver` 를 **IP가 아닌 client_id(JWT sub)** 기준으로
- [ ] 플랜별 한도 다르게 (FREE 초당 5 / PRO 초당 50) — 토큰의 `plan` claim 활용
- [ ] 일일 Quota(일 1,000회) 커스텀 필터 — Redis `INCR` + 자정 만료
- [ ] 응답 헤더 `X-RateLimit-Remaining`, 초과 시 429 + `Retry-After`

**동작 확인**
```bash
for i in $(seq 1 20); do curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/parkings; done
# 200 몇 번 후 429
```

**공부**: Token Bucket 알고리즘, Redis 카운터, 분산 환경에서 제한이 왜 Redis여야 하는지

---

## Phase 5. MSA로 나누기

**목표**: 서비스를 나누고, 서비스끼리 협력하게 만들기

- [ ] `discovery` (Eureka) 추가, 모든 서비스 등록, Gateway uri → `lb://서비스명`
- [ ] `developer-service` → `auth-server` 내부 API 호출로 client 등록 (서비스 간 동기 통신)
- [ ] 앱 삭제 시 Redis 블랙리스트 등록 → Gateway에서 즉시 차단
- [ ] `usage-service` 추가: Gateway가 호출 이벤트를 **비동기**로 전달 (처음엔 Redis Stream 또는 비동기 HTTP, 심화에서 Kafka)
- [ ] `GET /api/v1/usage/me` 로 내 사용량 조회
- [ ] Circuit Breaker: data-service 지연 시 fallback 503
- [ ] data-service 인스턴스 2개 띄워 로드밸런싱 확인

**동작 확인** (04-lab-architecture 의 장애 시나리오)
- usage-service 끄고 API 호출 → 정상 응답
- data-service 끄고 호출 → 503 표준 에러 (타임아웃까지 기다리지 않음)
- 앱 삭제 직후 기존 토큰으로 호출 → 401

**공부**: Service Discovery, 동기 vs 비동기, 장애 격리, 서비스별 DB

---

## Phase 6. 운영 준비

- [ ] 각 서비스 Dockerfile + `docker-compose.yml` 로 전체 기동
- [ ] Micrometer Tracing + Zipkin: Gateway → data-service 호출을 traceId 하나로 추적
- [ ] 에러 응답에 traceId 포함
- [ ] 로그에서 토큰/secret 마스킹 확인
- [ ] OpenAPI(Swagger) 문서를 Gateway에서 모아 보기 (외부 개발자용 API 문서)

---

## Phase 7. 심화 (선택)

> 실무 구성과의 차이는 [06-production-design.md](06-production-design.md) 참고

- [ ] auth-server 를 **Keycloak** 으로 교체 (Gateway 설정만 바꿔서 동작하는지 확인)
- [ ] Nginx(LB) + Gateway 2대 이중화, Gateway 1대 꺼도 서비스 유지 확인
- [ ] sandbox / prod 키 구분 (`sk_test_` / `sk_live_`)
- [ ] Redis 장애 시 fail-open / fail-closed 동작 구현 후 비교

- [ ] **Authorization Code + PKCE**: 외부 앱이 "우리 사용자"의 데이터에 동의받고 접근
- [ ] 개발자 포털 로그인도 OAuth2 로그인으로 전환
- [ ] 서명키 회전(kid 2개 동시 운영) 실습
- [ ] Introspection 방식과 JWT 방식 성능·즉시폐기 비교
- [ ] Kafka로 호출 이벤트 처리, 월별 과금 집계
- [ ] 내부 서비스도 JWT 재검증 (Zero Trust) vs 헤더 신뢰 비교
- [ ] Kong / Nginx 로 같은 구성 해보고 Spring Cloud Gateway와 비교
- [ ] Kubernetes (kind / minikube) + Ingress

---

## 진행 현황

| Phase | 내용 | 상태 | 완료일 | 메모 |
|---|---|---|---|---|
| 0 | 준비 | ⬜ | | |
| 1 | Gateway 라우팅 | ⬜ | | |
| 2 | API Key | ⬜ | | |
| 3 | OAuth2 + JWT | ⬜ | | |
| 4 | Rate Limit / Quota | ⬜ | | |
| 5 | MSA 분리 | ⬜ | | |
| 6 | 운영 준비 | ⬜ | | |
| 7 | 심화 | ⬜ | | |
