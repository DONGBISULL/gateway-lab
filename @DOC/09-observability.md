# 09. 메트릭·대시보드·알림 TODO

## 목표

개발 환경에서 서비스 상태를 수치로 확인하고, 운영 단계에서 이상 징후를 알림으로 받는다.

```text
Spring Boot Actuator ─┐
                       ├─ Prometheus ─→ Grafana 대시보드
Node Exporter ────────┘        │
                               └─ Alertmanager ─→ Slack
```

## 메트릭 책임

| 구분 | 수집기 | 대표 항목 | 용도 |
|---|---|---|---|
| 시스템 메트릭 | Node Exporter | CPU, 메모리, 디스크, 네트워크 | 호스트 자원 부족 확인 |
| JVM 메트릭 | Spring Actuator + Micrometer | heap, GC, thread 상태, process CPU | Java 런타임 상태 확인 |
| HTTP 메트릭 | Spring Actuator + Micrometer | 요청 수, 지연 시간, 4xx/5xx 비율 | API 품질 확인 |
| Gateway 메트릭 | Gateway 커스텀 메트릭 | 인증 실패, Circuit Breaker, Rate Limit | 진입점 이상 징후 확인 |
| 업무 메트릭 | 서비스 커스텀 메트릭 | 키 발급, 호출량, 폐기 수 | 제품 사용량 확인 |

## JVM Thread 확인

Actuator의 JVM 메트릭으로 thread 수와 상태별 수를 확인한다.

- live / daemon / peak thread 수
- `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING` 상태별 thread 수
- thread 수 급증, `BLOCKED` 상태가 일정 시간 이상 유지되는지

`BLOCKED` thread 수만으로 데드락을 확정하지 않는다. 지속적인 증가나 장애 알림이 있으면
`/actuator/threaddump`로 원인을 확인한다. 데드락 감지가 필요해질 때 `ThreadMXBean` 기반
커스텀 메트릭을 추가한다.

## 구현 순서

### 1. 개발 환경 메트릭 노출

- [ ] 각 서비스에 Prometheus Micrometer registry 추가
- [ ] `/actuator/prometheus`를 Prometheus 전용 네트워크에서만 노출
- [ ] `health`, `info`, `prometheus`의 Actuator 노출 정책 정의
- [ ] Gateway, data-service부터 메트릭 확인

### 2. Prometheus와 Grafana

- [ ] Docker Compose에 Prometheus, Grafana 추가
- [ ] Prometheus scrape 대상에 Gateway와 각 서비스 등록
- [ ] Grafana datasource provisioning 추가
- [ ] JVM, HTTP, Gateway 대시보드 JSON을 저장소에 관리

### 3. 시스템 메트릭

- [ ] Node Exporter 추가
- [ ] CPU, 메모리, 디스크 사용량 대시보드 추가
- [ ] 서비스 컨테이너 환경이면 cAdvisor 또는 컨테이너 런타임 메트릭 도입 여부 결정

### 4. 알림

- [ ] Alertmanager 추가
- [ ] Slack webhook은 `.env`로만 주입
- [ ] 5xx 비율, HTTP 지연 시간, Circuit Breaker OPEN, 디스크 부족 알림 정의
- [ ] `BLOCKED` thread 수가 임계값 이상으로 일정 시간 유지될 때 경고 알림 정의

### 5. Gateway·업무 메트릭

- [ ] API Key 인증 성공/실패 카운터
- [ ] Rate Limit / Quota 거부 카운터
- [ ] Circuit Breaker 상태·fallback 카운터
- [ ] developer-service 키 발급·폐기 카운터
- [ ] usage-service 호출량 집계 메트릭

## 보안 규칙

- Prometheus endpoint와 Grafana는 외부 인터넷에 직접 공개하지 않는다.
- Actuator 상세 endpoint는 관리자·내부 네트워크만 접근하도록 제한한다.
- Slack webhook, Grafana 비밀번호, Prometheus 인증 정보는 `.env`에만 둔다.
- API Key, JWT, Authorization 헤더, 요청 본문을 label 또는 로그에 넣지 않는다.

## 완료 확인

- [ ] Grafana에서 Gateway의 요청 수·p95 지연 시간·5xx 비율 확인
- [ ] Grafana에서 JVM heap·GC·thread 상태 확인
- [ ] Grafana에서 Node CPU·메모리·디스크 확인
- [ ] 고의 5xx 또는 Circuit Breaker OPEN 상황에서 Slack 경고 수신
- [ ] Alertmanager 알림이 해소되면 resolved 알림 수신
