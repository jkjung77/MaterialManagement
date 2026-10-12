---
name: push-github
description: >-
  만능자재관리 저장소의 변경을 커밋하고 GitHub에 푸시한다. 사용자가
  "모든 변경 사항을 github에 올려 줘"라고 하거나, 커밋해서 깃허브에
  올리라고 하면 사용한다.
---

# 모든 변경 사항을 GitHub에 올리기

저장소 루트에서 한다. 이 skill을 연 요청이 바로 커밋과 푸시 허가다. 확인을 다시 묻지 않는다. force push, `git config` 변경, `--no-verify`는 하지 않는다.

## 순서

1. 아래를 병렬로 실행한다.

```powershell
git status
git diff
git diff --staged
git log -8 --oneline
```

2. 커밋 메시지를 정한다. 최근 `git log` 문장 길이에 맞추고, 무엇을 나열하기보다 왜 바꿨는지를 한두 문장으로 쓴다.
3. 변경이 없으면 빈 커밋을 만들지 않고, 이미 깨끗하다고 알린다.
4. 추적 대상만 올린다. `git add -A`는 `.gitignore`가 빌드 결과와 비밀 파일을 빼 준다. 그래도 스테이징에 `keystore.properties`, `keystore/`, `*.jks`, `*.keystore`, `server/.env`, `app/build/`가 있으면 경로를 빼고 사용자에게 알린다.
5. PowerShell에서 커밋한다.

```powershell
git commit -m @"
메시지

"@
```

6. 훅이 실패하면 빈 커밋을 고치지 않는다. 원인을 고친 뒤 새 커밋을 만든다.
7. `git status`로 원격 추적 여부를 본다. 추적 중이면 `git push`, 아니면 `git push -u origin HEAD`.
8. 푸시 뒤 `git status`로 작업 트리가 깨끗한지, 브랜치가 원격과 맞는지 확인한다.

## 사용자에게 알릴 내용

한국어로 짧게 알린다.

- 커밋 해시와 메시지
- 푸시한 브랜치
- 뺀 파일이 있으면 그 이유
