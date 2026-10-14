---
name: branch
description: 팀 네이밍 컨벤션에 맞춰 브랜치명을 정하고 최신 원격 기준으로 분기한다. Use when the user asks for a branch, like "브랜치 만들어줘", "/branch 45 로그인", "이슈 102번 브랜치 파줘".
argument-hint: "[CATEGORY] [이슈번호] [설명]"
allowed-tools: Bash(git *) Bash(gh pr list *)
---

# Branch

명령어만 출력하지 않고 직접 분기한다.

## 규칙

- 분기 전에 `git fetch origin`하고 **`origin/` ref에서** 분기한다. 로컬 브랜치는 뒤처져 있을 수 있다.
- `--no-track`으로 분기한다. 기준 브랜치를 upstream으로 잡지 않기 위해서다.
- 이슈 번호 없이 분기하지 않는다. 없으면 `/issue`로 먼저 만들지 묻는다.
- 커밋되지 않은 변경을 임의로 stash하거나 커밋하지 않는다.

## 이름

```
{label}/{issue-number}[-{description}]
```

- description은 영문 소문자와 하이픈, 2~3 단어. 한 이슈에 브랜치가 둘 이상이면 필수다.
- 예: `fix/1-dispatch-tx-boundary`, `feature/4-circuit-breaker`
- 대문자, 언더스코어, 점, 번호 누락, 표의 **브랜치 라벨** 열에 없는 이름(`hotfix`, `bug`, `feat`)은 쓰지 않는다.

## 라벨 매핑 (단일 소스)

`/commit`, `/issue`가 이 표를 참조한다.

| 브랜치 라벨 | 커밋 타입 | 이슈 CATEGORY |
|---|---|---|
| feature | feat | FEATURE |
| fix | fix | FIX |
| refactor | refactor | REFACTOR |
| test | test | TEST |
| chore | chore | CHORE |
| docs | docs | DOCS |

커밋 메시지 형식(`type: 설명 (#N)`)은 [`/commit`](../commit/SKILL.md)이 정한다. 이 표는 그 표가 참조하는 타입 목록의 단일 소스다.

이 저장소에는 브랜치 라벨로 PR 라벨을 붙이는 workflow가 없다. PR 라벨은 이슈와 같은 라벨을 수동으로 붙인다.

## 절차

1. 라벨, 이슈 번호, 설명을 정한다. 모르면 묻는다.
2. `git fetch origin` 후 중복을 확인한다. `git branch -a`(로컬과 원격), `git worktree list`(다른 worktree 점유).
3. **기준을 묻는다.** 기본은 `origin/main`이다. 머지되지 않은 브랜치의 코드가 필요하면 그 위에 쌓는다(스택). 후보는 같은 라벨의 원격 브랜치와 열린 PR이다.

   ```bash
   git branch -r --format='%(refname:short)' | sed 's|^origin/||' | grep "^{label}/"
   gh pr list --state open --json number,headRefName
   ```

   ```
   fix/1-dispatch-tx-boundary 으로 분기합니다. 기준을 골라주세요.
   1. origin/main (기본, {해시})
   2. origin/fix/3-dispatch-claim (PR #20)
   ```

   후보가 없으면 묻지 않고 main으로 간다. "응", "진행해"는 1번이다.
4. 분기한다.

   ```bash
   git switch -c {브랜치명} --no-track origin/{기준}
   ```

5. 보고한다.

   ```
   {브랜치명} 으로 분기했습니다.
   기준: {기준 ref} ({해시 8자리}, {커밋 제목})
   ```

   스택이면 "main이 아닙니다. 앞 PR이 머지된 뒤 올리거나 base를 앞 브랜치로 둡니다"를 덧붙인다.

## 예외

- **이름 중복**: 만들지 않고 기존 브랜치로 옮길지, 다른 description을 쓸지 묻는다. 다른 worktree가 점유 중이면 그 경로를 알린다.
- **미커밋 변경 충돌로 switch 거부**: 사용자가 고르게 한다.
  1. 먼저 커밋한다
  2. 브랜치만 만든다 (`git branch {브랜치명} --no-track origin/{기준}`)
  3. 새 worktree에서 분기한다
  4. stash 후 분기하고 pop한다 (pop 충돌 시 직접 해결하지 않고 멈춘다)
- **detached HEAD 상태**: 현재 저장소는 `origin/feature3`에서 detach된 상태로 시작할 수 있다. 분기 기준은 현재 HEAD가 아니라 항상 `origin/` ref다.

## 첫 push

upstream이 없으므로 `git push -u origin {브랜치명}`으로 올린다. `/pr`이 이 형태로 푸시한다.
