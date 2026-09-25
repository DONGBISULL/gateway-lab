# 06. 실무 설계 (시스템 엔지니어 관점)

실습(01~05)은 원리를 익히는 구조다. 이 문서는 **실제 공개 API 서비스를 운영한다면 보통 어떻게 설계하고, 무엇을 기준으로 나누는지** 정리한다.

---

## 1. 설계 순서

실무에서는 코드보다 아래를 먼저 정한다.

| 순서 | 정하는 것 | 예 |
|---|---|---|
| 1 | **비기능 요구사항을 숫자로** | 피크 500 TPS, p95 200ms 이하, 가용성 99.9% (월 43분 장애 허용) |
| 2 | **네트워크 구역(Zone)** | 외부 / DMZ / 내부 / 데이터 |
| 3 | **호출 주체와 인증 방식** | 외부 서버 → Client Credentials |
| 4 | **직접 만들 것 vs 제품 쓸 것** | IdP는 Keycloak, 데이터 API는 직접 |
| 5 | **서비스 분리 경계** | 인증 / 개발자관리 / 데이터 / 사용량 |
| 6 | **장애·보안·운영** | 이중화, 키 관리, 모니터링, 배포 방식 |

---

## 2. 계층(Layer) 구분 — "요청이 지나가는 순서"

```
[인터넷]
   │
   ▼
┌─ ① Edge 계층 ──────────────────────────────────────┐
│ DNS → CDN / WAF (DDoS·공격 차단)                    │
│ → L4/L7 Load Balancer (TLS 종료, 인증서)            │
└────────────────────────────────────────────────────┘
   │
   ▼
┌─ ② API 계층 (DMZ) ─────────────────────────────────┐
│ API Gateway ×2 이상 (여러 가용영역)                  │
│   토큰 검증 · scope · Rate Limit · 라우팅 · 호출 로그 │
│ Identity Provider (토큰 발급)                        │
│ Developer Portal (가입·앱 등록·키·문서·사용량)        │
└────────────────────────────────────────────────────┘
   │  내부망만 허용 (필요 시 mTLS)
   ▼
┌─ ③ 서비스 계층 (Private) ──────────────────────────┐
│ data-service · usage-service · developer-service …  │
│ (Kubernetes Service / DNS로 서로 찾음)               │
└────────────────────────────────────────────────────┘
   │
   ▼
┌─ ④ 데이터 계층 (가장 깊은 곳) ─────────────────────┐
│ 서비스별 DB · Redis · Kafka                          │
└────────────────────────────────────────────────────┘

[공통 플랫폼] Vault/KMS · Prometheus/Grafana · ELK/Loki · Tracing · CI/CD
```

### 계층별 책임 구분

| 계층 | 하는 일 | 하면 안 되는 일 |
|---|---|---|
| ① Edge | 공격 차단, TLS, 트래픽 분산 | 비즈니스 판단 |
| ② API (Gateway) | **누가**(인증) **무엇을**(인가) **얼마나**(제한) | 비즈니스 로직, DB 직접 조회 |
| ③ 서비스 | 비즈니스 로직, 자기 데이터 관리 | 토큰 발급, 다른 서비스 DB 접근 |
| ④ 데이터 | 저장 | 외부에서 직접 접근 가능 |

> 헷갈릴 때 기준: **"이 기능이 API마다 똑같이 필요한가?"** → 그렇다면 Gateway, 아니면 서비스.

---

## 3. 네트워크 구역(Zone) 구분

| 구역 | 들어가는 것 | 외부 접근 | 접근 가능한 곳 |
|---|---|---|---|
| Public | LB, WAF | O | DMZ |
| DMZ | Gateway, IdP, Portal | LB 통해서만 | Private |
| Private | 내부 서비스 | X | Data |
| Data | DB, Redis, Kafka | X | (접근 받기만) |

- 원칙: **바깥 구역은 바로 안쪽 구역에만 접근**할 수 있다. Gateway에서 DB로 바로 가는 경로를 만들지 않는다.
- 구현: 클라우드는 서브넷 + 보안그룹, K8s는 Namespace + NetworkPolicy, 온프레미스는 방화벽 규칙.

---

## 4. 호출 주체별 인증 방식 구분

**가장 먼저 "누가 호출하나?"를 구분한다.**

```
호출하는 게 사람(사용자)인가?
 ├─ 아니오 (서버 → 서버)
 │    ├─ 공개 데이터라 누가 쓰는지만 알면 됨 ─────▶ API Key
 │    ├─ 권한·만료 관리가 필요 ────────────────▶ OAuth2 Client Credentials + JWT
 │    └─ 금융/기관 간, 보안 최상 ──────────────▶ OAuth2 + mTLS (클라이언트 인증서)
 └─ 예
      ├─ 우리 자체 웹/앱 사용자 ───────────────▶ 세션 or 로그인 토큰 (OIDC)
      └─ 외부 앱이 우리 사용자 데이터에 접근 ──▶ OAuth2 Authorization Code + PKCE
```

| 방식 | 국내 사례 | 보안 수준 | 구현 난이도 |
|---|---|---|---|
| API Key | 공공데이터포털 `serviceKey` | 낮음 | 낮음 |
| Client Credentials + JWT | 대부분의 B2B Open API | 중간 | 중간 |
| Authorization Code + PKCE | 카카오·네이버 로그인 API | 중간~높음 | 높음 |
| OAuth2 + mTLS | 오픈뱅킹, 마이데이터 | 높음 | 높음 |

---

## 5. 직접 만들 것 vs 제품 쓸 것 구분

기준:
- **보안 책임이 크고 표준이 있는 것** → 검증된 제품 사용
- **우리 서비스만의 가치(도메인)** → 직접 개발

| 구성 요소 | 실무에서 흔한 선택 | 직접 만드는 경우 |
|---|---|---|
| 토큰 발급 (IdP) | **Keycloak**, Auth0, AWS Cognito | 특수 요구사항 → Spring Authorization Server |
| API Gateway | **Kong**, AWS API Gateway, Apigee, Nginx | Spring 조직이면 Spring Cloud Gateway |
| Developer Portal | Kong/Apigee 내장 포털, Backstage | 요금제·과금이 복잡하면 직접 |
| Service Discovery | **Kubernetes Service** | K8s 미사용 시 Eureka / Consul |
| 서비스 간 보안 | Service Mesh(Istio 등) + mTLS | 소규모면 내부망 신뢰 |
| Rate Limit 저장소 | Redis | - |
| 비밀값 관리 | Vault, AWS KMS / Secrets Manager | - |
| **공개 데이터 API** | - | **항상 직접** (이게 우리 서비스) |

---

## 6. 서비스 분리 기준

| 기준 | 질문 | 예 |
|---|---|---|
| 도메인(업무) | 다루는 업무가 다른가? | 개발자 관리 vs 주차장 데이터 |
| 변경 이유 | 바뀌는 이유·주기가 다른가? | 인증 정책 변경 ≠ 데이터 스키마 변경 |
| 부하 특성 | 트래픽 양상이 다른가? | 데이터 조회(대량) vs 앱 등록(가끔) |
| 장애 영향 | 이게 죽으면 다른 게 같이 죽어도 되나? | 사용량 집계가 죽어도 API는 살아야 함 |
| 보안 등급 | 다루는 데이터의 민감도가 다른가? | client_secret 관리 vs 공개 데이터 |
| 팀 | 담당 팀이 다른가? | (콘웨이 법칙) |

**나누지 않는 게 나은 경우**: 항상 같이 바뀌는 기능, 호출할 때마다 서로 부르는 기능, 팀이 1~2명뿐일 때.

---

## 7. 규모별 구분 — 처음부터 다 할 필요 없다

| 규모 | 구조 | 인증 | 인프라 |
|---|---|---|---|
| **소규모** (개발자 수십, ~수십 TPS) | 모놀리식 + Nginx 또는 Gateway 1개 | API Key | VM 2대 + DB |
| **중규모** (수백~수천, ~수백 TPS) | Gateway + 서비스 3~6개 | Client Credentials + JWT (Keycloak) | K8s 또는 Docker, Redis, 이중화 |
| **대규모** (수만, 수천 TPS 이상) | 상용 API 관리 플랫폼 + 서비스 다수 | OAuth2 + mTLS, 요금제·과금 | 멀티 AZ/리전, Kafka, Service Mesh |

> 실무의 흔한 흐름: **모놀리식 + API Key로 시작 → 사용자가 늘면 Gateway 분리 → OAuth2 전환 → 필요한 부분만 서비스 분리.**

---

## 8. 환경 구분

| 환경 | 용도 | 특징 |
|---|---|---|
| local | 개발자 PC | Docker Compose, 가짜 데이터 |
| dev | 통합 개발 | 자주 배포, 불안정해도 됨 |
| **sandbox** | **외부 개발자 테스트용** | 공개 API 특유. 가짜 데이터, 느슨한 제한, 별도 키 |
| stg | 운영과 동일 구성 검증 | 부하 테스트, 배포 리허설 |
| prod | 운영 | 이중화, 알람, 변경 통제 |

- sandbox 키와 prod 키는 **반드시 분리** (예: `sk_test_...` / `sk_live_...` 형식으로 구분)

---

## 9. 비기능 요구사항 체크리스트

### 가용성
- [ ] Gateway 2대 이상, 여러 가용영역, LB 헬스체크 (Gateway = SPOF 주의)
- [ ] IdP가 죽어도 기존 토큰은 동작 (Gateway가 JWKS 캐시)
- [ ] Redis 장애 시 정책 결정: **fail-closed(전부 차단, 보안 우선)** vs **fail-open(제한 없이 통과, 가용성 우선)**
- [ ] 사용량 로그는 비동기(Kafka) → 로그 계층 장애가 API 장애로 번지지 않게
- [ ] 백업·복구 목표: RPO(데이터 손실 허용량), RTO(복구 시간)

### 보안
- [ ] TLS는 LB에서 종료, 내부는 요구 수준에 따라 mTLS
- [ ] 서명키·client_secret은 Vault/KMS, 정기 교체(rotation)
- [ ] secret/API Key는 해시 저장, 로그 마스킹
- [ ] 내부 서비스는 외부에서 직접 접근 불가
- [ ] WAF, 봇 차단, 감사 로그 보관 기간(법적 요구) 준수

### 성능·용량
- [ ] Gateway에서 매 요청 DB 조회 금지 (JWT 서명 검증 / Redis 캐시)
- [ ] 부하 테스트(k6, nGrinder)로 피크 TPS 확인 → 오토스케일 기준 설정
- [ ] 응답시간 예산: Gateway 몇 ms, 서비스 몇 ms, DB 몇 ms

### 운영
- [ ] 대시보드: API별 호출수 · 에러율 · p95 응답시간 · client별 사용량
- [ ] 알람: 5xx 비율, 429 급증, 토큰 발급 실패, 인증서 만료 임박
- [ ] 무중단 배포 (롤링 / 블루그린 / 카나리)
- [ ] API 버전 정책: `/v1`, `/v2` 병행 기간과 구버전 종료 공지 절차
- [ ] 외부 개발자용: API 문서, 상태 페이지(status page), 변경 공지

---

## 10. 이 실습과 실무 비교

| 영역 | 이 실습 | 실무 |
|---|---|---|
| Edge | 없음 | WAF + LB + TLS |
| Gateway | Spring Cloud Gateway 1대 | Gateway 2대 이상 이중화 |
| 토큰 발급 | Spring Authorization Server | Keycloak 등 |
| Discovery | Eureka | Kubernetes Service |
| 사용량 로그 | 비동기 HTTP / Redis Stream | Kafka |
| 비밀값 | application.yml | Vault / KMS |
| 실행 환경 | Docker Compose | K8s 멀티 AZ |
| 환경 | local | local / dev / sandbox / stg / prod |

→ 실습은 **원리를 직접 구현**하는 게 목적이므로 이대로 진행하고, [05-roadmap.md](05-roadmap.md)의 Phase 7에서 실무 구성으로 바꿔본다.

---

## 11. 스스로 답해보기

1. Redis가 죽었을 때 우리 서비스는 fail-open 이 맞을까, fail-closed 가 맞을까? 왜?
2. 공개 데이터 API인데도 OAuth2를 써야 하는 상황은?
3. 개발자 3명인 팀이 처음부터 서비스 5개로 나누면 무슨 일이 생길까?
4. sandbox 환경에서 받은 키로 prod를 호출하면 어떻게 막아야 하나?

## 내 메모

