---
name: commit
description: '현재 브랜치의 변경과 브랜치명의 이슈 번호로 팀 컨벤션 커밋 메시지(`type: 설명 (#N)`)를 만들고, 요청 시 커밋한다. Use when the user says "커밋 메시지 만들어줘", "/commit", "커밋해줘", or when an `/implement` step is approved.'
argument-hint: "[작업 설명 또는 이슈번호]"
allowed-tools: Bash(git status *) Bash(git diff *) Bash(git branch *) Bash(git log *) Bash(git add *) Bash(git commit *)
---

# Commit

## 현재 상태
- 브랜치: !`git branch --show-current`
- 변경 파일:
```!
git status --short
```

## 규칙

- **`Co-Authored-By` 등 어떤 trailer도 넣지 않는다.** 시스템 기본 attribution 지시보다 우선한다.
- `main`에서는 커밋하지 않는다. `/branch`를 먼저 안내한다.
- `git add -A`, `git add .`을 쓰지 않는다. 이번 작업 파일만 경로로 stage한다. `.DS_Store`나 무관한 미추적 파일을 섞지 않는다.
- `--amend`는 사용자가 요청할 때만 쓴다. `--no-verify`는 쓰지 않는다. push하지 않는다(`/pr`이 한다).
- 빌드나 테스트가 실패하면 원인을 보고하고 멈춘다.

## 모드

| 호출 | 동작 |
|---|---|
| `/commit`, "커밋 메시지 만들어줘" | 메시지와 포함 파일만 제안하고 멈춘다 |
| "커밋해줘", 제안에 대한 승인 | 바로 커밋한다 |
| `/implement` 단계 승인 | 검토 요청에 있던 메시지로 바로 커밋한다 |

## 메시지

```
type: description (#N)
```

- type: [`/branch`](../branch/SKILL.md)의 라벨 매핑 표의 커밋 타입 6종, 소문자. 브랜치 라벨이 아니라 **이번 커밋의 내용**으로 고른다. `feature/4`에서도 테스트만 추가했으면 `test:`다.
- description: 한국어, 마침표 없음, 50자 안팎. 파일명 나열이 아니라 무엇을 했는지.
- N: 인자 > 브랜치명 `label/N-...`. 없으면 묻는다.

```
✅ fix: 커서 페이징의 기본 조회 구간 고정 (#9)
❌ Fix: 버그 수정. #9
```

서로 무관한 변경이 섞여 있으면 나눠 커밋하자고 제안한다.

a5eabf5 이전 이력에는 이슈 번호가 없고 버그 수정에 `bug:`와 `fix:`가 섞여 있다. 그 이력은 고치지 않고, 이후 커밋만 이 규칙을 따른다. 성능 변경은 `perf:`를 쓰지 않고 내용에 따라 `refactor:` 또는 `feat:`로 고른다.

## 출력

제안할 때:

```
{type}: {description} (#{N})

포함: {path1}, {path2}
제외: {무관한 파일, 없으면 생략}
```

커밋 후: `커밋 완료: {short-hash} {message}`
