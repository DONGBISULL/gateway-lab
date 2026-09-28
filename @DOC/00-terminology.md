# 00. 용어와 클래스 이름 규칙

## Gateway 용어

| 용어 | 책임 | 이름 예시 |
|---|---|---|
| Authentication | 자격 증명 검증, 호출자 확인 | `ApiKeyAuthenticator` |
| Principal | 인증 결과의 호출자 정보 | `AuthenticatedPrincipal` |
| Sanitize | 외부 요청의 신뢰 불가 값 제거 | `RequestSanitizer` |
| Passport | Gateway가 내부 서비스에 전달하는 호출자 컨텍스트 | `GatewayPassport` |
| Issuer | Passport와 신뢰 헤더 발급 | `GatewayPassportIssuer` |
| Codec | 데이터의 직렬화·역직렬화 | `GatewayPassportCodec` |
| Headers | Gateway 소유 헤더 이름 설정 | `GatewayHeadersProperties` |

## 이름 규칙

- 인터페이스: 역할을 나타내는 명사형 — `RequestSanitizer`, `ApiKeyAuthenticator`
- 구현체: 대상 + 역할 — `HeaderRequestSanitizer`, `LocalApiKeyAuthenticator`
- Gateway 필터: 처리 대상 + `Filter` — `RequestSanitizationFilter`, `ApiKeyAuthenticationFilter`
- 설정 바인딩: 설정 범위 + `Properties` — `GatewayHeadersProperties`
- 데이터 모델: 도메인 명사 — `GatewayPassport`, `AuthenticatedPrincipal`
- 변환 전용 클래스: 대상 + `Codec` — `GatewayPassportCodec`
- 발급 전용 클래스: 대상 + `Issuer` — `GatewayPassportIssuer`
- `Util`, `Helper`, `Manager`, `Common`처럼 책임이 드러나지 않는 이름은 사용하지 않는다.

## Gateway 소유 헤더

헤더 이름은 `gateway/src/main/resources/application.yml`의 `app.headers`에서만 정의한다.

| 설정 키 | 기본 헤더 | 외부 입력 | downstream |
|---|---|---|---|
| `api-key` | `X-API-Key` | 인증에 사용 | 인증 후 제거 |
| `client-id` | `X-Client-Id` | 제거 | Gateway가 설정 |
| `passport` | `X-Gateway-Passport` | 제거 | Gateway가 설정 |
| `trace-id` | `X-Trace-Id` | 제거 | Gateway가 설정 |
