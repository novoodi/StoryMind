# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 소개

소설 집필 앱. 화(chapter) 저장 시 온디바이스 Gemma(LiteRT-LM)가 엔티티/관계를 추출해 위키와 그래프로 누적하는 구조.

Android 단일 모듈(`:app`), Jetpack Compose + Room + LiteRT-LM (`gemma-4-E2B-it.litertlm`, 기기의 `getExternalFilesDir("models")`에 있어야 동작).

## 명령어

```bash
./gradlew test                                # 단위 테스트 (JVM) — 모든 변경 후 통과 필수
./gradlew connectedAndroidTest                # 계측 테스트 (기기/에뮬레이터 필요, 모델 파일도 필요)
./gradlew :app:assembleDebug                  # 디버그 빌드
./gradlew :app:testDebugUnitTest --tests "com.example.storymind.ai.IngestServiceTest"   # 단일 테스트 클래스
```

Windows에서는 `gradlew.bat`. `androidTest`의 `OnDeviceEngineSmokeTest` 등은 기기에 모델 파일이 설치되어 있어야 통과한다.

`connectedAndroidTest`(및 `connectedDebugAndroidTest`)는 종료 시 앱을 기기에서 제거한다 — 실행이 끝나면 앱과 함께 `getExternalFilesDir("models")`의 모델 파일도 사라진다. 계측 테스트 실행 후 앱을 수동으로 열어보거나 다른 테스트 클래스를 이어서 돌리려면, 그 전에 `./gradlew installDebug` + `scripts/push-model.sh`(Windows는 `scripts/push-model.bat`)를 다시 실행해야 한다.

DB 마이그레이션의 권위 있는 검증은 `connectedAndroidTest`가 필요하다 (`StoryDatabaseMigrationTest`, `MigrationTestHelper`로 실제 스키마 identity hash까지 검증). `src/test`의 동명 JVM 테스트는 DDL이 기존 행을 보존하는지 보는 빠른 스모크일 뿐 Room 자체 검증을 타지 않는다 — 마이그레이션을 "검증됐다"고 여기려면 기기/에뮬레이터에서 `connectedAndroidTest`까지 통과해야 한다.

## 아키텍처 불변 규칙 (절대 어기지 말 것)

1. **챕터 원고(`ChapterEntity.body`)는 불변 소스 오브 트루스.** AI도 코드도 수정 금지. 인제스트 결과는 원고에서 파생되어 별도 테이블에 저장된다 (`StoryEntities.kt`, `StoryRepository.kt`의 KDoc 참고). `ChapterProgress`는 파생 스냅샷이며 인제스트마다 통째로 재저장된다.
2. **노드 좌표와 고아(orphan) 판정은 모델이 아니라 코드가 결정적으로 계산한다.** 좌표는 `NodeLayout.layoutNodes`(원형 배치), 고아 판정은 `IngestService`/`ChapterProgress.merge`(누적 엣지 집합 기준). `IngestSchema`의 프롬프트도 모델에게 좌표·고아 판정을 만들지 말라고 명시한다. 모델의 역할은 추출(엔티티/관계/설명/요약)에서 끝난다.
3. **원고 저장 성공이 다음 화 진행 조건이다. 인제스트 성공이 아니다.** `StoryViewModel.saveAndIngest`는 원고를 먼저 저장하고 `canAdvance = true`로 만든 뒤에야 인제스트를 시도한다. 인제스트가 실패해도 원고는 저장된 상태로 남고 작가는 다음 화로 진행할 수 있다 (`StoryUiState.canAdvance` KDoc 참고). 이 분리를 깨지 마라.
4. **`ai/` 패키지의 도메인 로직에 새 Android 의존성을 추가하지 마라.** 향후 KMP `commonMain` 이동 예정. `android.util.Log`는 `IngestLogger` 인터페이스(`ai/Logger.kt`)로, `org.json`은 `kotlinx.serialization.json`으로 이미 교체됐다 — Android 구현체(`AndroidIngestLogger`)는 `com.example.storymind.platform` 패키지에 분리되어 있고, `StoryViewModel` 초기화 시점에 `ingestLogger`에 연결된다. `OnDeviceEngine`만이 ai/ 패키지 안에서 유일하게 허용된 Android/LiteRT-LM 경계다 — `ai/`에 `android.*` import가 남아있지 않은지는 `grep -n "^import android\." app/src/main/java/com/example/storymind/ai`로 확인하고, `OnDeviceEngine.kt` 외에는 나오면 안 된다.
5. **`IngestParser`의 복구 정규식은 로컬 엔진 경로의 정식 구성 요소다. 지우지 마라.** `docs/constrained-decoding-spike.md`의 결론대로 constrained decoding(JSON 스키마 강제 출력)은 당분간 도입하지 않는다 — LiteRT-LM 0.13.1에는 응답 스키마 API가 없고, 실험 플래그는 툴콜 경로에 묶여 있다. 따라서 Gemma의 JSON 슬립은 프롬프트 + `IngestParser` 수리 + `IngestService` 재생성(3회)으로 흡수하는 것이 정식 설계다.
6. **`StoryDatabase`의 스키마(엔티티 필드, `@Database version`)를 바꾸면 `Migration` 작성은 물론, `schemas/`에 새 버전의 내보내기 JSON을 커밋하고 그 JSON을 사용하는 `MigrationTestHelper` 계측 테스트를 추가해야 한다.** `exportSchema = true` + `ksp { arg("room.schemaLocation", ...) }`로 버전마다 스키마가 `app/schemas/<DB 전체 클래스명>/<version>.json`에 내보내진다. 이 JSON이 없으면 `MigrationTestHelper.createDatabase`가 그 버전의 실제 DB를 만들 수 없고, `runMigrationsAndValidate`가 하는 identity-hash/컬럼 단위 구조 검증도 돌릴 수 없다 — 즉 `fallbackToDestructiveMigration` 없이 진짜 마이그레이션이 맞는지 기기에서 증명할 방법이 없어진다. 과거 버전 JSON을 소급 생성해야 한다면(`exportSchema`를 이번에 처음 켠 경우 등), 엔티티/버전을 그 과거 상태로 임시로 되돌려(`git stash`) 빌드한 뒤 원복하는 방식을 쓴다.

## 계층 구조

- **`ai/`** — 인제스트 파이프라인. `OnDeviceEngine`(LiteRT-LM 래퍼, GPU 실패 시 CPU 폴백, 문자열 in/out만 담당) → `IngestSchema`(프롬프트 빌드) → `IngestParser`(Gemma의 일회성 JSON 오류들을 정규식으로 수리, 실패 시 빈 결과 폴백) → `IngestService`(파싱 실패 시 최대 3회 재생성, 기존 엔티티 id 재사용/접미사 이름 매칭, 중복 제거) → `ChapterProgress.merge`(화별 결과를 누적 위키/그래프로 병합). `IngestService`는 `OnDeviceTextEngine` fun interface를 받으므로 테스트에서 가짜 엔진으로 대체한다.
  같은 패키지에 별도 파이프라인으로 설정 충돌 검사(린트)가 있다: `LintSchema`(사고 모드 켠 프롬프트, `LintCandidate`의 위키 이력을 시간순으로 제시) → `LintParser`(사고 블록 제거가 필수 경로, 인제스트 수리 정규식을 스키마별로 재검토해 일부만 재사용) → `LintService`(이력 2화 미만 엔티티는 엔진 호출 없이 스킵, 파싱 실패 시 최대 2회 재생성, `LintResult.succeeded`로 "검사해서 문제 없음"과 "검사 실패"를 구분). v1은 서비스 계층까지만 — DB 저장도 UI 연동도 없다.
- **`data/`** — Room 영속성. `StoryDatabase`(정식 `Migration` 체인, `fallbackToDestructiveMigration` 금지 — 사용자 원고가 든 DB), `StoryDao`, `StoryRepository`, 엔티티↔도메인 매퍼(`StoryEntities.kt`). `MockData`는 모델이 없을 때의 프리뷰/폴백 데이터.
- **`work/`** — `IngestWorker`(CoroutineWorker). 저장 시 enqueue되어 인제스트를 프로세스 죽음 너머까지 완주시킨다: gate 안에서 DB의 최신 원고/누적 위키를 읽고 `IngestService`를 돌린 뒤 `StoryRepository.commitIngest`(단일 트랜잭션: body 가드→merge→replace→출처 스탬프)로 커밋. 챕터별 unique work(REPLACE)로 중복 enqueue 방지. WorkManager 의존은 이 패키지에만 둔다.
- **`platform/`** — Android 경계 구현체. `AndroidIngestLogger`(ai/ 로깅 심의 Android 구현), `IngestEngineProvider`(앱 스코프 엔진 싱글턴 — refcount로 마지막 사용자가 나가면 네이티브 해제, `ingestGate` Mutex로 워커 간 인제스트 전체를 직렬화).
- **`ui/`** — Compose. `StoryMindApp`(탭 네비게이션 등 순수 네비 상태만 로컬), `StoryViewModel`(Room·UI 상태 소유, 프로세스 종료 후 복원 — 엔진은 소유하지 않으며 인제스트는 `IngestWorker`의 WorkInfo를 관찰만 한다), `screens/`(Editor/Brain/Wiki/Settings), `components/`(Sm* 접두사 디자인 시스템: SmButton, SmCard, SmBadge 등), `theme/`(SmColors — 브랜드 컬러 Toss Blue #1F4EF5 고정, 다이나믹 컬러 금지).

엔티티 타입(인물/장소/소품/사건)은 `ui/components`의 `SmBadgeType` enum이 도메인 전체에서 공용으로 쓰인다 (순수 Kotlin enum이라 `ai/`, `data/`에서도 참조).

## 코드 컨벤션

기존 코드처럼 **"왜 이렇게 했는지"를 설명하는 KDoc 주석을 유지**하라. 이 저장소의 주석은 무엇을 하는지가 아니라 왜 그렇게 설계했는지(예: `IngestParser`의 각 정규식이 수리하는 Gemma 특유의 오류 패턴, `OnDeviceEngine.MAX_NUM_TOKENS`를 올린 이유)를 기록한다. 새 코드도 비자명한 결정에는 같은 수준의 근거를 남길 것.
