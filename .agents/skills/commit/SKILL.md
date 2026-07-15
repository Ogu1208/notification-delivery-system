---
name: commit
description: Use when writing, reviewing, amending, or creating Git commit messages in this repository.
---

# Commit

Use this skill before creating or amending a commit in this repository.

## Format

```text
type: description
```

Do not include an issue number.

## Types

Use exactly one of these lowercase types:

| Type | Meaning |
| --- | --- |
| `feat` | New feature or behavior change |
| `bug` | Bug fix |
| `refactor` | Refactoring without behavior change |
| `test` | Test code |
| `chore` | Build, package, config, or operational changes |
| `docs` | Documentation changes |

## Description

- Write in Korean when possible.
- Keep it concise, preferably 50 characters or less.
- Do not end with a period.

## Examples

```text
feat: 티켓 상태 검증을 도메인 엔티티 내부로 통합
refactor: 좌석 예매 엔티티 역정규화
bug: 예매 취소 시 상태 업데이트 실패 수정
test: 환불 관련 테스트 코드 추가
chore: 부하 테스트 스크립트 추가
docs: 예매 API 명세 문서 작성
```

## Before Committing

1. Inspect the staged diff.
2. Choose the narrowest matching type.
3. Split unrelated changes into separate commits.
4. Run relevant verification when the change can affect behavior.
5. Commit with `git commit -m "<message>"`.
