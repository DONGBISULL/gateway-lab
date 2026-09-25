# gateway-lab

Gateway를 중심으로 서비스를 나눠 보는 Spring 기반 MSA 실습 프로젝트입니다.
공영주차장 조회를 예시 도메인으로 삼아, 각 서비스가 독립적으로 동작하고 협력하는 구조를 단계적으로 구현합니다.

## 실습 범위

- Gateway를 단일 진입점으로 두고 요청을 서비스에 라우팅합니다.
- 인증 서버, 데이터 서비스, 개발자 서비스, 사용량 서비스를 독립 모듈로 나눕니다.
- 서비스 탐색(Eureka), 동기·비동기 통신, 장애 격리를 순서대로 적용합니다.
- 인증은 API Key에서 OAuth 2.0 Client Credentials + JWT까지 확장합니다.
- 필요해지는 시점에 Rate Limit, 호출량 집계, Redis 캐시를 추가합니다.

## 구성 모듈

| 모듈 | 역할 | 포트 |
|---|---|---:|
| `gateway` | 외부 API 진입점, 인증·인가, 제한, 라우팅 | 8080 |
| `auth-server` | OAuth 2.0 토큰 발급 및 JWKS 공개 | 9000 |
| `data-service` | 공영주차장 공개 API | 8081 |
| `developer-service` | 개발자·앱·자격 증명·플랜 관리 | 8084 |
| `usage-service` | API 호출 이력과 사용량 집계 | 8085 |
| `discovery` | Eureka 기반 서비스 탐색 | 8761 |

## 예시 API

| API | 설명 | 인증 |
|---|---|---|
| `POST /oauth2/token` | 액세스 토큰 발급 | Client Credentials |
| `GET /api/v1/parkings` | 주차장 목록 조회 | `data.read` scope |
| `GET /api/v1/parkings/{id}` | 주차장 상세 조회 | `data.read` scope |
| `GET /api/v1/usage/me` | 내 API 사용량 조회 | `stats.read` scope |
| `/portal/apps` | 개발자 앱 등록 및 자격 증명 관리 | 포털 로그인 |

> 모든 외부 요청은 Gateway를 거칩니다. 서비스별 책임과 요청 흐름은 [실습 구조 설계](@DOC/04-lab-architecture.md)에 정리했습니다.

## 현재 상태

현재는 작은 단위로 구현·검증·커밋하는 단계입니다. 먼저 Gateway 라우팅과 데이터 서비스를 연결하고, 이후 인증·제한·서비스 간 통신을 추가합니다. 구현 순서와 완료 기준은 [실습 로드맵](@DOC/05-roadmap.md)을 따릅니다.

## 문서

- [API Gateway](@DOC/01-api-gateway.md)
- [마이크로서비스](@DOC/02-microservices.md)
- [Open API 인증](@DOC/03-open-api-auth.md)
- [실습 구조 설계](@DOC/04-lab-architecture.md)
- [실습 로드맵](@DOC/05-roadmap.md)
- [실무 설계](@DOC/06-production-design.md)

## 기술 구성

Java 21, Spring Boot 4.1.1, Spring Cloud Gateway, Spring Authorization Server, Eureka, Redis, Gradle을 사용합니다. 이후 Docker Compose, Resilience4j, 분산 추적을 단계적으로 추가합니다.
