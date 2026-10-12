---
name: make-aab
description: >-
  만능자재관리 앱의 versionCode·versionName을 올리고 Play용 release AAB를
  빌드한다. 사용자가 버전 올리고, aab 빌드, bundleRelease, make_aab를
  말하거나 릴리스 번들을 만들어 달라고 하면 사용한다.
---

# 버전 올리고 AAB 빌드

저장소 루트에서 한다. 커밋, 푸시, Play Console 업로드는 사용자가 따로 말하기 전에는 하지 않는다.

## 올리지 말 것

`app/build.gradle.kts`에서 버전 두 줄만 바꾼다. AGP, Kotlin, `applicationId`, `minSdk`, `targetSdk`, 의존성은 그대로 둔다.

## 순서

1. `app/build.gradle.kts`의 `versionCode`, `versionName`과 설정의 버전 문구를 읽는다. 설정 문구는 `MoreSettingsScreens.kt`의 `버전 x.y.z · 서버 동기화 · Material 3`이다.
2. 사용자가 버전을 지정하지 않았으면 `versionCode`는 1 올리고, `versionName`은 마지막 숫자만 1 올린다. 예: `33` / `1.1.31` → `34` / `1.1.32`. 사용자가 버전 이름을 지정했으면 그 이름을 쓰고, `versionCode`는 현재보다 최소 1 크게 한다.
3. 두 곳의 버전 표시를 같게 고친다.
4. 저장소 루트에서 빌드한다.

```powershell
.\gradlew.bat bundleRelease --offline -q
```

오프라인 실패가 의존성 다운로드 때문이면 `--offline` 없이 한 번만 다시 실행한다.

5. `app/build/outputs/bundle/release/app-release.aab`가 있고, 수정 시각이 이번 빌드 이후인지 확인한 뒤에만 성공이라고 말한다.

## 사용자에게 알릴 내용

한국어로 짧게 알린다.

- `versionName`과 `versionCode`
- aab 경로, 크기, 만든 시각
- Play Console에 붙여 넣을 변경 내용. 이번 버전에 들어간 사용자에게 보이는 변화만 적는다. 구현 설명은 넣지 않는다.
- 커밋하지 않았다는 한 줄
