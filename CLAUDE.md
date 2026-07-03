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

`gradle.properties`의 `android.injected.androidTest.leaveApksInstalledAfterRun=true`가 `connectedAndroidTest`(및 `connectedDebugAndroidTest`) 종료 시 앱을 기기에서 제거하는 AGP 기본 동작을 끈다 — 이 플래그가 없으면 실행이 끝나며 앱과 함께 `getExternalFilesDir("models")`의 모델 파일도 사라져서, 계측 테스트 실행 후 앱을 열어보거나 다른 테스트 클래스를 이어서 돌리려면 매번 `./gradlew installDebug` + `scripts/push-model.sh`(Windows는 `scripts/push-model.bat`)를 다시 해야 했다. 이 플래그로 그 재설치·재푸시 단계는 보통 필요 없다 — 앱을 실제로 수동 제거했거나 기기를 교체한 경우에만 다시 필요하다.

DB 마이그레이션의 권위 있는 검증은 `connectedAndroidTest`가 필요하다 (`StoryDatabaseMigrationTest`, `MigrationTestHelper`로 실제 스키마 identity hash까지 검증). `src/test`의 동명 JVM 테스트는 DDL이 기존 행을 보존하는지 보는 빠른 스모크일 뿐 Room 자체 검증을 타지 않는다 — 마이그레이션을 "검증됐다"고 여기려면 기기/에뮬레이터에서 `connectedAndroidTest`까지 통과해야 한다.

## 아키텍처 불변 규칙 (절대 어기지 말 것)

1. **챕터 원고(`ChapterEntity.body`)는 불변 소스 오브 트루스.** AI도 코드도 수정 금지. 인제스트 결과는 원고에서 파생되어 별도 테이블에 저장된다 (`StoryEntities.kt`, `StoryRepository.kt`의 KDoc 참고). `ChapterProgress`는 파생 스냅샷이며 인제스트마다 통째로 재저장된다. 파생 데이터는 설정 화면의 "AI 데이터 재구축"으로 언제든 원고에서 재생 가능하다(`ReplayWorker`, work/ 항목 참고) — 잘못 쌓인 위키/그래프는 고치는 게 아니라 지우고 다시 만든다.
2. **노드 좌표와 고아(orphan) 판정은 모델이 아니라 코드가 결정적으로 계산한다.** 좌표는 `NodeLayout.layoutNodes`(원형 배치), 고아 판정은 `IngestService`/`ChapterProgress.merge`(누적 엣지 집합 기준). `IngestSchema`의 프롬프트도 모델에게 좌표·고아 판정을 만들지 말라고 명시한다. 모델의 역할은 추출(엔티티/관계/설명/요약)에서 끝난다.
3. **원고 저장 성공이 다음 화 진행 조건이다. 인제스트 성공이 아니다.** `StoryViewModel.saveAndIngest`는 원고를 먼저 저장하고 `canAdvance = true`로 만든 뒤에야 인제스트를 시도한다. 인제스트가 실패해도 원고는 저장된 상태로 남고 작가는 다음 화로 진행할 수 있다 (`StoryUiState.canAdvance` KDoc 참고). 이 분리를 깨지 마라.
4. **`ai/` 패키지의 도메인 로직에 새 Android 의존성을 추가하지 마라.** 향후 KMP `commonMain` 이동 예정. `android.util.Log`는 `IngestLogger` 인터페이스(`ai/Logger.kt`)로, `org.json`은 `kotlinx.serialization.json`으로 이미 교체됐다 — Android 구현체(`AndroidIngestLogger`)는 `com.example.storymind.platform` 패키지에 분리되어 있고, `StoryViewModel` 초기화 시점에 `ingestLogger`에 연결된다. `OnDeviceEngine`만이 ai/ 패키지 안에서 유일하게 허용된 Android/LiteRT-LM 경계다 — `ai/`에 `android.*` import가 남아있지 않은지는 `grep -n "^import android\." app/src/main/java/com/example/storymind/ai`로 확인하고, `OnDeviceEngine.kt` 외에는 나오면 안 된다.
5. **`IngestParser`의 복구 정규식은 로컬 엔진 경로의 정식 구성 요소다. 지우지 마라.** `docs/constrained-decoding-spike.md`의 결론대로 constrained decoding(JSON 스키마 강제 출력)은 당분간 도입하지 않는다 — LiteRT-LM 0.13.1에는 응답 스키마 API가 없고, 실험 플래그는 툴콜 경로에 묶여 있다. 따라서 Gemma의 JSON 슬립은 프롬프트 + `IngestParser` 수리 + `IngestService` 재생성(3회, 저온→고온 샘플러 에스컬레이션)으로 흡수하는 것이 정식 설계다.
   - `IngestParser.parseOrNull`의 수리 체인은 한 번만 도는 게 아니라 **출력이 더 안 바뀔 때까지 반복**한다(`repairToFixpoint`, 최대 5회). 한 패스의 수리가 다른 패스가 찾는 패턴을 새로 만들어내는 간섭이 있을 수 있어서다(2화 인제스트 실패, 2026-07: 콤마 누락 수리가 만든 결과가 스트레이 쿼트-콤마 수리의 입력 패턴과 우연히 겹침). 패스 순서 재배열은 그 조합 하나만 고치지만, 고정점 반복은 이 부류 전체를 없앤다.
   - `IngestService.ingest()`는 3회 재생성 후에도 파싱이 안 되면 **빈 결과를 커밋하지 않는다** — `null`을 반환하고, `IngestWorker`는 커밋 없이 `Result.failure()`로 끝낸다(WorkManager 자동 재시도는 걸지 않음 — 1회 생성이 분 단위라 비용이 큼). 작가는 에디터의 실패 배지(`SmAiStatus.Warning`)에서 `StoryViewModel.retryIngest()`로 수동 재시도한다. 원고 저장과 `canAdvance`는 이 실패와 무관하게 유지된다(규칙 3). 실패 시 마지막 raw 응답은 `IngestFailureRecorder`를 통해 기기에 저장되어 회귀 픽스처 후보가 된다.
6. **`StoryDatabase`의 스키마(엔티티 필드, `@Database version`)를 바꾸면 `Migration` 작성은 물론, `schemas/`에 새 버전의 내보내기 JSON을 커밋하고 그 JSON을 사용하는 `MigrationTestHelper` 계측 테스트를 추가해야 한다.** `exportSchema = true` + `ksp { arg("room.schemaLocation", ...) }`로 버전마다 스키마가 `app/schemas/<DB 전체 클래스명>/<version>.json`에 내보내진다. 이 JSON이 없으면 `MigrationTestHelper.createDatabase`가 그 버전의 실제 DB를 만들 수 없고, `runMigrationsAndValidate`가 하는 identity-hash/컬럼 단위 구조 검증도 돌릴 수 없다 — 즉 `fallbackToDestructiveMigration` 없이 진짜 마이그레이션이 맞는지 기기에서 증명할 방법이 없어진다. 과거 버전 JSON을 소급 생성해야 한다면(`exportSchema`를 이번에 처음 켠 경우 등), 엔티티/버전을 그 과거 상태로 임시로 되돌려(`git stash`) 빌드한 뒤 원복하는 방식을 쓴다.

## 계층 구조

- **`ai/`** — 인제스트 파이프라인. `OnDeviceEngine`(LiteRT-LM 래퍼, GPU 실패 시 CPU 폴백, 문자열 in/out만 담당 — `SamplerSettings`를 넘기면 그 설정으로, 안 넘기면 엔진 기본 샘플러로 생성) → `IngestSchema`(프롬프트 빌드) → `IngestParser`(Gemma의 일회성 JSON 오류들을 정규식으로 수리; 안정될 때까지 최대 5회 반복하는 `repairToFixpoint`, 규칙 5 참고) → `IngestService`(파싱 실패 시 최대 3회 재생성하며 온도 0.1→0.4→0.7로 샘플러 에스컬레이션, 기존 엔티티 id 재사용/접미사 이름 매칭, 중복 제거; 3회 모두 실패하면 `null` 반환 — 규칙 5) → `ChapterProgress.merge`(화별 결과를 누적 위키/그래프로 병합). `IngestService`는 프롬프트+`SamplerSettings`를 함께 받는 `IngestTextEngine` fun interface를 쓰고, `LintService`는 샘플러 없는 `OnDeviceTextEngine`을 그대로 쓴다(lint의 샘플링은 이번 변경의 영향을 받지 않는다) — 둘 다 테스트에서 가짜 엔진으로 대체 가능. 최종 실패의 마지막 raw 응답은 `IngestFailureRecorder`로 기기에 저장되어 회귀 픽스처 후보가 된다.
  같은 패키지에 별도 파이프라인으로 설정 충돌 검사(린트)가 있다: `LintSchema`(사고 모드 켠 프롬프트, `LintCandidate`의 위키 이력을 시간순으로 제시) → `LintParser`(사고 블록 제거가 필수 경로, 인제스트 수리 정규식을 스키마별로 재검토해 일부만 재사용) → `LintService`(이력 2화 미만 엔티티는 엔진 호출 없이 스킵, 파싱 실패 시 최대 2회 재생성, `LintResult.succeeded`로 "검사해서 문제 없음"과 "검사 실패"를 구분). `LintWorker`(work/)가 `IngestEngineProvider.ingestGate`를 인제스트와 공유하며 "설정 검사" 버튼으로 온디맨드 실행되고, 결과는 `StoryViewModel`이 관찰해 `SmLintResultSheet`로 보여준다 — DB에는 아무것도 쓰지 않는 일회성 검사다.
- **`data/`** — Room 영속성. `StoryDatabase`(정식 `Migration` 체인, `fallbackToDestructiveMigration` 금지 — 사용자 원고가 든 DB), `StoryDao`, `StoryRepository`, 엔티티↔도메인 매퍼(`StoryEntities.kt`). `MockData`는 모델이 없을 때의 프리뷰/폴백 데이터.
- **`work/`** — `IngestWorker`(CoroutineWorker). 저장 시 enqueue되어 인제스트를 프로세스 죽음 너머까지 완주시킨다: gate 안에서 DB의 최신 원고/누적 위키를 읽고 `IngestService`를 돌린 뒤 `StoryRepository.commitIngest`(단일 트랜잭션: body 가드→merge→replace→출처 스탬프)로 커밋. `IngestService.ingest()`가 `null`이면(재생성 3회 모두 파싱 실패) 커밋 없이 `Result.failure()`로 끝난다 — 자동 재시도는 걸지 않고, `StoryViewModel.retryIngest()`를 통한 사용자 액션으로만 재시도한다. 챕터별 unique work(REPLACE)로 중복 enqueue 방지. WorkManager 의존은 이 패키지에만 둔다.
  `ReplayWorker`는 파생 데이터 전체 재구축(리플레이). 재구축의 정의는 "`StoryRepository.resetDerivedData`(단일 트랜잭션: 파생 4개 테이블 비움 + 전 챕터 `ingested=false`/출처 NULL, 원고 body 무접촉) 실행 후, `ingested=false`인 비어있지 않은 챕터를 오름차순으로 순차 인제스트"다. 이 정의가 곧 멱등 재개 원리다: 각 실행이 "아직 누적에 없는 가장 낮은 챕터"를 집어들 뿐이므로, 중간 실패 후 재시도는 재개 전용 코드 없이 같은 연산의 재실행(리셋 없는 resume)이고, 재구축 도중 저장된 새 화도 그냥 가장 높은 미인제스트 챕터로서 큐 끝에 자연히 붙는다. 한 실행이 한 챕터만 인제스트하고 남으면 스스로 후속을 unique work(`APPEND_OR_REPLACE`)로 재enqueue한다 — WorkManager의 10분 실행 제한 때문. 순서는 WorkManager 체인이 아니라 구조로 보장된다: 다음 챕터 선택이 `ingestGate` 안에서 일어나고, `IngestWorker`의 순서 가드(자기보다 낮은 미인제스트 챕터가 있으면 실패로 물러남)가 저장 시 워커의 끼어들기를 막는다. 실패한 화에서 체인이 멈추고(후속 미enqueue — 화 건너뛰기는 누적 병합을 깨므로 금지) `StoryViewModel.retryIngest()`의 resume이 그 화부터 이어간다. 인제스트의 생성→커밋 코어는 `ChapterIngest`로 추출되어 `IngestWorker`와 공유한다.
- **`platform/`** — Android 경계 구현체. `AndroidIngestLogger`(ai/ 로깅 심의 Android 구현), `AndroidIngestFailureRecorder`(ai/의 실패 raw 응답 기록 심의 Android 구현), `IngestEngineProvider`(앱 스코프 엔진 싱글턴 — refcount로 마지막 사용자가 나가면 네이티브 해제, `ingestGate` Mutex로 워커 간 인제스트 전체를 직렬화; `withTextEngine`은 `OnDeviceTextEngine`, `withIngestTextEngine`은 샘플러 에스컬레이션용 `IngestTextEngine`을 내준다).
- **`ui/`** — Compose. `StoryMindApp`(탭 네비게이션 등 순수 네비 상태만 로컬), `StoryViewModel`(Room·UI 상태 소유, 프로세스 종료 후 복원 — 엔진은 소유하지 않으며 인제스트는 `IngestWorker`의 WorkInfo를 관찰만 한다; FAILED는 `SmAiStatus.Warning`으로 뜨고 `retryIngest()`가 `ReplayWorker`의 resume을 enqueue해 실패한 화부터 미인제스트 챕터 전부를 순서대로 처리한다; 재구축 진행/중단은 `RebuildUiState`로 관찰해 브레인/위키의 `SmRebuildBanner`에 "재구축 중 n/m화"로 표시), `screens/`(Editor/Brain/Wiki/Settings), `components/`(Sm* 접두사 디자인 시스템: SmButton, SmCard, SmBadge 등), `theme/`(SmColors — 브랜드 컬러 Toss Blue #1F4EF5 고정, 다이나믹 컬러 금지).

엔티티 타입(인물/장소/소품/사건)은 `ui/components`의 `SmBadgeType` enum이 도메인 전체에서 공용으로 쓰인다 (순수 Kotlin enum이라 `ai/`, `data/`에서도 참조).

## 코드 컨벤션

기존 코드처럼 **"왜 이렇게 했는지"를 설명하는 KDoc 주석을 유지**하라. 이 저장소의 주석은 무엇을 하는지가 아니라 왜 그렇게 설계했는지(예: `IngestParser`의 각 정규식이 수리하는 Gemma 특유의 오류 패턴, `OnDeviceEngine.MAX_NUM_TOKENS`를 올린 이유)를 기록한다. 새 코드도 비자명한 결정에는 같은 수준의 근거를 남길 것.
