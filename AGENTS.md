# AI 에이전트 개발 규칙 (Claude / GPT 등 공통)

이 저장소에서 코드를 작성하는 모든 AI 코딩 에이전트(Claude Code, GPT 기반 에이전트 등)가
반드시 지켜야 할 규칙입니다. 사람이 직접 작성할 때도 동일하게 적용됩니다.

## 1. 비밀값(Secret)은 코드에 절대 하드코딩하지 않는다

API Key, client_secret, DB 비밀번호, JWT 서명키, 토큰 등 민감한 값은
소스 코드(`.java`)나 커밋되는 설정 파일(`application.yml` 등)에 **리터럴로 직접 쓰지 않는다.**
"학습용 임시값", "일단 테스트용" 이라는 이유도 예외가 아니다 — 임시값일수록 나중에
지우는 걸 잊기 쉽고, 그대로 커밋되면 git 히스토리에 영구히 남는다.

**규칙:**
- 모듈별 `.env`(git-ignored) + `.env.example`(커밋용, placeholder 값만) 패턴을 따른다.
  - 레퍼런스: `data-service/.env.example`, `gateway/.env.example`
- `application.yml`에서는 `spring.config.import: optional:file:.env[.properties]`로 로드하고,
  값은 `${ENV_VAR}` 형태로만 참조한다.
- **포트 번호처럼 민감하지 않은 값**은 `${SERVER_PORT:8081}`처럼 기본값을 둬도 된다.
- **비밀값에는 기본값을 두지 않는다.** `.env`에 값이 없으면 애플리케이션이 기동 실패하는 게 맞다
  (실수로 박아둔 기본값이 그대로 운영까지 새어나가는 사고를 막기 위함).

**위반 예시 (실제로 있었던 문제, 수정됨):**
```java
// 나쁨 — 코드에 키가 그대로 박혀 있고, 커밋되면 영구히 히스토리에 남음
private static final String VALID_API_KEY = "test-key-123";
```
```java
// 좋음 — 값은 .env(API_KEY)에서만 주입
public ApiKeyFilter(@Value("${app.security.api-key}") String validApiKey) { ... }
```
```yaml
# application.yml — 기본값 없이 필수 값으로 선언
app:
  security:
    api-key: ${API_KEY}
```

## 2. 새 모듈을 추가할 때

- `.env.example` 파일을 만들고, 실제 값이 들어간 `.env`는 커밋하지 않는다.
  루트 `.gitignore`가 이미 `.env` / `.env.*`를 막고 `.env.example`만 예외 처리한다 — 새 규칙을 또 추가할 필요 없음.
- 모듈 자체 `.gitignore`를 새로 만들 필요는 없다(루트에서 이미 커버됨). 다만 그 모듈만의 특이한
  산출물(예: 로컬 DB 파일)이 있으면 그때 추가한다.

## 3. API Key / 비밀번호는 저장할 때 반드시 해시

DB에 평문으로 저장하지 않는다. BCrypt 등으로 해시해서 저장한다.
(`@DOC/05-roadmap.md` Phase 2 규칙과 동일 — "DB엔 해시로 저장")

## 4. 로그에 민감정보를 남기지 않는다

토큰, API Key, client_secret 값 자체를 로그(`log.info`, `System.out` 등)로 출력하지 않는다.
(`@DOC/05-roadmap.md` Phase 6: "로그에서 토큰/secret 마스킹 확인")

## 5. 커밋 전 확인

새 파일을 스테이징할 때, 시크릿이 포함된 파일(특히 `.env`, `*.pem`, `*.key`)이 실수로
같이 올라가지 않는지 `git status` / `git diff --cached`로 확인한다.
