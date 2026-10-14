# 에이전트 작업 지침

## 프로젝트 컨텍스트

이 프로젝트는 Java, Spring Boot 3.x, Gradle, H2, Kafka, Redis를 사용하는 알림 발송 시스템 과제다.

Kafka와 Redis 의존성은 해당 구현 단계에서 필요해질 때 추가한다.

## 개발 워크플로우

작업 단위는 GitHub 이슈다. 이슈에서 시작해 PR로 끝낸다.

1. `/issue [CATEGORY] [작업 내용]`: 팀 컨벤션에 맞춘 이슈를 만든다.
2. `/branch [CATEGORY] [이슈번호] [설명]`: `origin/main` 기준으로 작업 브랜치를 분기한다.
3. `/implement [이슈번호]`: 계획을 세우고 "구현 → 테스트 → 검토 → 승인 → 커밋"을 단계마다 반복한다.
4. `/commit`: `type: 설명 (#N)` 형식으로 커밋한다. 단계 승인 시 `/implement`가 호출한다.
5. `/pr [이슈번호]`: 전체 테스트가 통과한 뒤 사용자 검수를 받아 PR을 생성한다.

스킬 문서는 `.agents/skills/` 아래에 두고, `.claude/skills`가 이를 심볼릭 링크로 가리킨다.
각 스킬의 규칙이 단일 소스다. 커밋 타입 목록은 `/branch`의 라벨 매핑표, 커밋 메시지 형식은 `/commit`이 정한다.

구현 안에서는 [`superpowers`](https://github.com/obra/superpowers) 시리즈를 함께 쓴다.

- 요구사항이 모호하면 `superpowers:brainstorming`으로 먼저 탐색한다.
- 구현은 `superpowers:test-driven-development`를 따른다. 버그 수정은 실패하는 테스트를 먼저 쓴다.
- 완료를 주장하기 전에 `superpowers:verification-before-completion`으로 `./gradlew test` 결과를 확인한다.
- 버그 발생 시 가장 먼저 `superpowers:systematic-debugging`을 사용한다.
- 리뷰는 `superpowers:requesting-code-review`로 요청하고, 받은 지적은 `superpowers:receiving-code-review`로 검토한 뒤 반영한다.
- 독립이 확인된 이슈가 둘 이상이면 `superpowers:dispatching-parallel-agents`로 병렬 처리한다.

문서 반영은 구현과 같은 PR에 포함한다. 바뀐 내용을 관련 문서(`AGENTS.md`, `README.md`, `docs/*`)에 반영하고, 기존 문서를 수정하거나 필요하면 적절한 문서를 신규 생성한다.

`superpowers` 보조 문서와 `/implement` 계획 파일(`docs/superpowers/plans/{N}.md`)은 `docs/superpowers/` 아래에 둔다. 이 디렉터리는 로컬 작업 기록 성격이므로 Git 추적에서 제외한다.

## 아키텍처

아키텍처 규칙의 기준 문서는 `아키텍처가이드.md`다.

핵심 규칙:

- 헥사고날 아키텍처 스타일을 따른다.
- `domain`, `application`, `adapter` 경계를 명확히 둔다.
- 애플리케이션 inbound port는 `application/{feature}/provided` 아래에 둔다.
- 애플리케이션 outbound port는 `application/{feature}/required` 아래에 둔다.
- Controller와 Kafka Consumer는 provided port에 의존한다.
- Application Service는 domain 객체와 required port에 의존하고, adapter 구현체에 직접 의존하지 않는다.
- Web API, JPA, Kafka, Redis, Mock 외부 발송 구현은 adapter 계층에 둔다.

## 문서 관리

구현하면서 계획 문서와 도메인 문서를 코드와 함께 갱신한다.

- 구현 순서, 범위, 주요 설계 판단이 바뀌면 `구현계획.md`를 갱신한다.
- 도메인 필드, 상태, 행위, 규칙이 바뀌면 `도메인모델.md`를 갱신한다.
- 용어가 추가되거나 이름과 의미가 바뀌면 `용어사전.md`를 갱신한다.
- 구현 계획 문서에는 상세 아키텍처 규칙을 중복 작성하지 말고 `아키텍처가이드.md`를 참고하게 한다.
- `용어사전.md`는 한국어, 영어, 설명 컬럼을 가진 표 형식을 유지한다.
- `도메인모델.md`는 도메인 개요를 먼저 쓰고, 이후 애그리거트/엔티티/값 객체/Enum을 속성, 행위, 규칙으로 정리하는 형식을 유지한다.

## 현재 구현 방향

단일 Gradle 모듈로 시작하고, 패키지 수준에서 헥사고날 경계를 유지한다.
명시적으로 요청받기 전에는 멀티모듈 구조를 도입하지 않는다.

권장 구현 순서:

1. API, 도메인 모델, 애플리케이션 포트, 애플리케이션 서비스, JPA 영속성을 먼저 구현한다.
2. 기본 요청 저장 흐름이 동작한 뒤 Kafka Producer를 추가한다.
3. Kafka Consumer와 Mock Sender를 추가한다.
4. 외부 발송 직전에 Redis 분산락 처리를 추가한다. Redis는 Consumer가 Mock Sender를 호출하기 직전에 같은 알림의 동시 발송을 막는 용도로 사용한다.

## 코딩 기준

- 작고 집중된 변경을 선호한다.
- 가능하면 도메인 로직은 도메인 객체 안에 둔다.
- Application Service는 유스케이스 흐름과 트랜잭션 경계를 담당한다.
- Kafka, Redis, Web framework 타입을 domain과 application port로 누수시키지 않는다.
- 코드 변경 후 가능하면 `./gradlew test`를 실행한다.

## 커밋 메시지 규칙

커밋 메시지는 아래 형식을 따른다.

```text
type: description
```

### 타입

| 타입 | 설명 |
| --- | --- |
| `feat` | 새로운 기능 또는 기존 기능 관련 |
| `bug` | 버그 수정 |
| `refactor` | 리팩터링 |
| `test` | 테스트 관련 |
| `chore` | 빌드, 패키지, 설정 등 운영 |
| `docs` | 문서 관련 |

### 형식 규칙

- `type`: 위 6종 중 하나를 소문자로 쓴다.
- `description`: 한글로 작성하고 50자 이내를 권장한다.
- 이슈 번호는 커밋 메시지에 포함하지 않는다.
- 마침표는 붙이지 않는다.

예시:

```text
feat: 티켓 상태 검증을 도메인 엔티티 내부로 통합
refactor: 좌석 예매 엔티티 역정규화
bug: 예매 취소 시 상태 업데이트 실패 수정
test: 환불 관련 테스트 코드 추가
chore: 부하 테스트 스크립트 추가
docs: 예매 API 명세 문서 작성
```
