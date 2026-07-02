# 인제스트를 viewModelScope → WorkManager로 이전

## Context

현재 `StoryViewModel.saveAndIngest`(StoryViewModel.kt:102)는 viewModelScope에서 저장→인제스트를 수행한다. 문제:

- 앱 프로세스가 죽으면 진행 중이던 인제스트가 유실된다 (원고는 저장돼 있지만 위키/그래프 미갱신, 재시도 주체 없음).
- `OnDeviceEngine`을 ViewModel이 소유해 `onCleared`에서 `runBlocking { engine.release() }`(StoryViewModel.kt:184)로 메인 스레드를 블로킹한다.
- 현재도 잠재 레이스가 있다: 저장 직후 `canAdvance=true`이므로 사용자가 다음 화를 쓰고 저장하면 두 번째 `saveAndIngest` 코루틴이 첫 번째와 겹친다. 두 번째 인제스트가 `progress.wikiEntries`를 첫 번째 merge 이전에 읽어 프롬프트 컨텍스트가 빠질 수 있다 (엔진 Mutex가 generate만 직렬화할 뿐).

목표: 저장 즉시 `ChapterEntity(ingested=false)` 커밋 + `IngestWorker` enqueue. 워커가 프로세스 죽음을 넘어 완주하면 위키/그래프가 갱신되고 출처 스탬프가 찍힌다. **CLAUDE.md 규칙 3(저장 성공 = 진행 조건)은 그대로 유지** — 오히려 저장과 인제스트의 분리가 프로세스 경계까지 확장된다. 규칙 4도 유지 — `ai/`는 한 줄도 수정하지 않는다.

**스키마 변경 없음** (DB v2 유지). `ingested`/`ingestEngine`/`ingestPromptVersion` 컬럼으로 충분하므로 규칙 6의 마이그레이션 부담이 발생하지 않는다. "마지막 챕터 `ingested=false` → 초안으로 복원"이라는 기존 의미도 유지된다: 오늘도 인제스트 실패 시 같은 상태가 되며, 비동기화로 그 시간 창이 길어질 뿐이다. 워커가 나중에 성공하면 플래그가 true로 바뀌어 다음 실행 시 정상 진행된다.

## 1. 파일별 변경 목록

### 신규

| 파일 | 내용 |
|---|---|
| `app/src/main/java/com/example/storymind/StoryMindApplication.kt` | `Application` 서브클래스. `ingestLogger = AndroidIngestLogger` 배선을 ViewModel init에서 이관 (워커가 ViewModel 없이 프로세스 재시작으로 실행돼도 로거가 연결돼 있어야 함). 매니페스트 `android:name` 등록. |
| `app/src/main/java/com/example/storymind/platform/IngestEngineProvider.kt` | 앱 스코프 엔진 싱글턴 + 생명주기 (아래 §3). `platform/` 패키지에 두어 `ai/`를 건드리지 않음. 프로세스 전역 `ingestGate: Mutex`도 여기 소유 (아래 §2). |
| `app/src/main/java/com/example/storymind/work/IngestWorker.kt` | `CoroutineWorker`. WorkManager 의존은 이 패키지에만. companion에 `enqueue(context, chapterIndex)` (unique name `"ingest-chapter-{index}"`, `ExistingWorkPolicy.REPLACE`) + `uniqueNameFor(chapterIndex)` 헬퍼. |
| `app/src/androidTest/java/com/example/storymind/work/IngestWorkerTest.kt` | 가짜 엔진 + in-memory Room으로 워커 end-to-end 검증 (모델 파일 불필요, §5). |
| `app/src/test/java/com/example/storymind/ui/AiStatusMappingTest.kt` | WorkInfo.State → SmAiStatus 순수 함수 JVM 테스트. |

### 수정

| 파일 | 변경 |
|---|---|
| `StoryViewModel.kt` | 엔진/`IngestService` 소유 제거, `onCleared` 삭제. `saveAndIngest` → 저장 + `IngestWorker.enqueue`. `getWorkInfosForUniqueWorkFlow` 관찰 → `SmAiStatus` 매핑 + SUCCEEDED 시 progress 리로드 (§4). |
| `StoryRepository.kt` | 생성자를 `StoryDatabase`로 변경(`db.withTransaction` 필요). 신규: `commitIngest(chapterIndex, ingestedBody, result, engine, promptVersion)` — **단일 트랜잭션** 안에서 body 가드 확인→load→`merge()`→replace→스탬프 전부 수행 (§2). merge와 스탬프 사이에서 프로세스가 죽으면 WorkManager 재실행이 같은 화를 중복 merge하므로 둘을 분리하지 않는다. |
| `StoryDao.kt` | 신규 `loadChapter(chapterIndex)`, `markIngested` — `UPDATE chapters SET ingested = 1, ingestEngine, ingestPromptVersion WHERE chapterIndex = :i`로 플래그만 갱신(규칙 1: body 불변, upsert 재작성 회피). body 가드는 같은 트랜잭션 안 `commitIngest` 진입부에서 확인 — "인제스트한 원고 ≠ 현재 원고"면 merge와 스탬프 둘 다 스킵된다 (재저장 레이스 보험, §2). |
| `AndroidManifest.xml` | `android:name=".StoryMindApplication"`. |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | `androidx.work:work-runtime-ktx` (2.10.x), `androidTestImplementation androidx.work:work-testing`. |

`ai/` 패키지, `schemas/`, 화면(`EditorScreen` 등)은 무변경. `StoryDatabase.kt`는 스키마 무변경(버전 2 유지)이며 워커 테스트용 `setInstanceForTesting` 심만 추가.

### IngestWorker.doWork 흐름

```
inputData: chapterIndex
ingestGate.withLock {                                  // 프로세스 전역 직렬화
  chapter = dao에서 body 로드 (없거나 이미 ingested면 success 반환)
  existingWiki = repository.loadProgress().wikiEntries  // gate 안에서 읽어 최신 보장
  provider.withEngine { engine ->                       // refcount 획득, §3
    result = IngestService(engine::generate).ingest(label, label, paragraphs, existingWiki)
  }
  repository.commitIngest(chapterIndex, body, result,   // 단일 Room 트랜잭션:
      "local", IngestSchema.PROMPT_VERSION)             // body 가드→merge→replace→스탬프
}
성공 → Result.success() / 예외 → Result.failure()      // 재시도 없음 = 기존 정책
gate 대기·생성·전체 소요 시간을 IngestLogger로 기록      // 10분 한도/게이트 적체 진단용
```

- `IngestService`는 fun interface `OnDeviceTextEngine`을 받으므로 워커에서 `engine::generate`로 그대로 재사용 — `ai/` 무수정.
- 모델 파일 없으면 ViewModel이 enqueue 자체를 안 함 (기존 `isModelAvailable` 선체크 유지).
- 내부 3회 재생성 후 빈 결과 폴백이면 오늘처럼 "성공"으로 취급 (빈 merge + 스탬프) — 기존 동작 동일.
- 예외 시 `Result.failure()`: 원고 저장 유지, 위키 미갱신, `canAdvance` 영향 없음 — 기존 실패 정책 그대로.

## 2. ChapterProgress 동시성 분석

**시나리오 A — 워커가 progress 갱신 중 UI가 읽을 때.**
`StoryDao.replaceProgress`는 이미 `@Transaction`(StoryDao.kt:63)이므로 UI 리더는 커밋 전 스냅샷 전체 또는 커밋 후 스냅샷 전체만 본다 — 반쯤 지워진 테이블을 볼 수 없다. UI는 progress를 직접 폴링하지 않고 WorkInfo SUCCEEDED를 보고 `loadProgress()`로 리로드하므로(§4), 읽기 시점엔 항상 커밋 완료 후다.

**시나리오 B — 다음 화 저장으로 워커 2개가 이어질 때 (ch n 실행 중 + ch n+1 enqueue).**
unique name이 챕터별이라 WorkManager는 둘의 순서를 보장하지 않는다. 두 겹의 방어:

1. **순서: `IngestEngineProvider.ingestGate` (프로세스 전역 Mutex)** — 워커 본문 전체(컨텍스트 읽기→생성→merge→스탬프)를 감싼다. ch n+1의 워커는 ch n의 merge 커밋 후에야 `existingWiki`를 읽으므로 프롬프트에 ch n의 엔티티가 포함된다. 이는 현재 viewModelScope 구현보다 개선이다(위 Context의 기존 레이스 해소). WorkManager는 기본적으로 앱 프로세스에서 워커를 실행하므로 프로세스 Mutex로 충분하다.
2. **원자성: `repository.commitIngest`** — `db.withTransaction { body 가드 → load → ChapterProgress.merge(result) → replaceProgress → markIngested }`. read-modify-write가 한 트랜잭션이므로 gate가 뚫려도(미래의 리팩터 실수 등) lost update는 불가능하고, merge와 스탬프 사이 프로세스 사망 시 WorkManager 재실행이 같은 화를 중복 merge하는 경로도 없다(스탬프가 보이면 스킵, 안 보이면 merge도 안 된 상태). `merge()` 호출은 트랜잭션 안이지만 순수 함수라 즉시 반환 — LLM 생성(수 분)은 트랜잭션 밖에서 이미 끝난 상태.

**시나리오 C — 같은 챕터 재저장 (중복 enqueue).**
`enqueueUniqueWork("ingest-chapter-{i}", REPLACE, ...)`:
- 대기 중(ENQUEUED)이던 기존 워커는 취소되고 새 워커가 대체 — 워커는 body를 inputData가 아닌 DB에서 읽으므로 항상 최신 원고를 인제스트.
- 실행 중(RUNNING)이던 워커도 취소된다. 네이티브 `sendMessage`는 중단 불가지만(§3), 취소된 코루틴은 다음 suspend 지점(Room 호출 등)에서 CancellationException으로 죽어 `commitIngest`에 도달하지 못한다. 취소가 늦게 전달돼 `commitIngest`까지 실행되더라도, 트랜잭션 진입부의 body 가드가 "인제스트한 원고 ≠ 현재 원고"인 경우 merge와 스탬프를 통째로 스킵한다 — 새 워커가 새 원고를 다시 인제스트하고 정상 커밋.
- 알려진 기존 quirk 유지: 같은 챕터를 두 번 인제스트하면 `merge`의 desc 누적이 그 화 설명을 중복 추가할 수 있음 — 현재 구현도 동일하며 이번 범위 밖.

## 3. 엔진 생명주기

`IngestEngineProvider` (object 또는 Application 소유 싱글턴):

- **초기화**: lazy. 최초 `withEngine { }` 호출 시 `OnDeviceEngine(appContext)` 생성 + `initialize()`. `isModelAvailable`은 초기화 없이 노출 (파일 존재 확인뿐이므로).
- **사용**: `suspend fun <T> withEngine(block: suspend (OnDeviceEngine) -> T): T` — 진입 시 refcount++, `finally`에서 refcount--. **refcount가 0이 되면 `engine.release()`로 네이티브 메모리 해제.** Gemma E2B는 GB급 RAM을 점유하므로 유휴 시 잡아두지 않는다. 연속 챕터 저장 시엔 ch n+1 워커가 ingestGate에 대기하는 동안… 주의: gate 대기는 `withEngine` 밖이므로 ch n 종료 시 refcount 0 → 해제 → ch n+1이 재초기화(수 초 로드)한다. 허용 가능한 트레이드오프로 판단 (인제스트 자체가 수 분 단위, 메모리 안전이 우선). 
- **워커 취소 시**: `withEngine`의 `finally`가 CancellationException 경로에서도 refcount를 내리고 필요 시 release한다. 단, 진행 중인 네이티브 `sendMessage`는 중단할 방법이 없다(LiteRT-LM 0.13.1에 abort API 없음) — 생성이 끝날 때까지 백그라운드에서 돌다가 결과는 버려지고, `OnDeviceEngine`의 내부 Mutex 해제 후 release가 실제로 수행된다. release 자체가 `mutex.withLock`이므로 사용 중 close가 발생하지 않는 기존 안전성 유지.
- **ViewModel**: 엔진을 전혀 소유하지 않음. `onCleared`와 `runBlocking { engine.release() }` **삭제** (목표 달성). `isModelAvailable`만 provider에서 조회.
- **테스트 심**: provider에 `@VisibleForTesting` 엔진 팩토리 오버라이드(가짜 `OnDeviceTextEngine` 주입용) — §5의 계측 테스트가 모델 파일 없이 워커를 돌리기 위함.

## 4. UI 상태 매핑

ViewModel은 "관찰 대상 챕터"(마지막으로 저장/enqueue한 chapterIndex; 복원 시엔 마지막 저장 챕터)를 유지하고 `WorkManager.getWorkInfosForUniqueWorkFlow(uniqueNameFor(index))`를 `flatMapLatest`로 수집한다.

| WorkInfo 상태 | SmAiStatus | 비고 |
|---|---|---|
| (없음 / null) | Idle | 아직 저장 안 함 |
| ENQUEUED / RUNNING / BLOCKED | Analyzing | "살펴보는 중이에요" 배지 |
| SUCCEEDED | **Done** | + `loadProgress()` 리로드로 wiki/graph/orphan 갱신, `lastSaveIngested=true` |
| FAILED | Idle | 기존 실패 정책: 조용히 Idle, 원고는 저장됨 |
| CANCELLED | Idle | 재저장에 의한 REPLACE 등 |

- **프로세스 재시작 복원**: WorkInfo는 WorkManager DB에 영속되므로 재시작 후에도 흐른다. ENQUEUED/RUNNING이면 Analyzing으로 복원(진행 중 표시 복원 요구 충족). 단 **종결 상태(SUCCEEDED 등)는 재시작 직후엔 Idle로 매핑** — 세션 내 enqueue 없이 과거 완료 기록만으로 "완료됐어요" 배지가 영구히 뜨는 것을 막는다 (`enqueuedThisSession` 플래그 또는 최초 emission 무시로 구현). 재시작 시 위키/그래프는 어차피 init의 `loadProgress()`가 워커 결과를 포함해 읽는다.
- 워커가 백그라운드/사용자가 이미 다음 화로 넘어간 뒤 SUCCEEDED가 도착해도 매핑은 동일 적용 — Done 배지가 뒤늦게 표시되는 것은 수용 (다음 저장/`startNextChapter`가 Idle로 리셋).
- `EditorScreen`은 무수정: `saving = aiStatus == Analyzing`(EditorScreen.kt:100) 등 기존 prop 그대로.
- 매핑은 `fun WorkInfo.State?.toAiStatus(...): SmAiStatus` 순수 함수로 분리해 JVM 테스트 가능하게.

## 5. 테스트 전략

**JVM (`./gradlew test`) — 로직 검증:**
- `AiStatusMappingTest`: 상태 매핑 함수 전수 검사 (WorkInfo.State는 평범한 enum이라 unit test에서 참조 가능).
- 기존 `IngestServiceTest`/`ChapterProgressTest`/`IngestParserTest`: `ai/` 무수정이므로 그대로 통과해야 함 — 회귀 게이트.
- 워커의 오케스트레이션 자체(WorkManager, Room)는 JVM으로 검증 불가 — 계측으로.

**계측 (`./gradlew connectedAndroidTest`) — 모델 파일 불필요 그룹:**
- `IngestWorkerTest` (`work-testing`의 `TestListenableWorkerBuilder` + in-memory Room 주입 + provider에 가짜 엔진 주입):
  - 성공 경로: 챕터 저장(ingested=false) → 워커 실행 → wiki/graph 테이블 갱신 + `ingested=true` + `ingestEngine="local"` + `ingestPromptVersion` 스탬프 확인.
  - 실패 경로: 엔진이 throw → `Result.failure()`, 챕터 `ingested=false` 유지, progress 테이블 무변화.
  - body 가드: 워커 실행 중 body가 바뀐 상황을 시뮬레이션 → 스탬프 미적용 확인.
  - 순차 실행: ch1 워커 → ch2 워커 순서로 돌려 ch2의 existingWiki에 ch1 엔티티가 보였는지(id 재사용) + merge 누적 확인.
- unique work 중복 방지: `WorkManagerTestInitHelper`로 같은 챕터 2회 enqueue → 대기 워커 1개(REPLACE) 확인.

**계측 — 모델 파일 필요 (기존 관행 유지):** `OnDeviceEngineSmokeTest`류는 그대로. 실제 Gemma로 워커 완주하는 스모크는 선택적 추가.

**수동 검증:** 모델 설치 기기에서 챕터 저장 직후 앱 강제 종료(`adb shell am force-stop`) → 재실행 시 워커가 완주해 위키/그래프 갱신 + Analyzing 배지 복원 확인.

## 리스크 / 열어두는 것

- **WorkManager 10분 실행 한도**: CPU 폴백 + 긴 챕터면 초과 가능. 이번엔 일반 워커로 시작하고, 초과가 관측되면 `setForeground`(장기 실행 워커, 알림 필요) 전환을 후속으로. 계획에서 구조 변경 없이 추가 가능한 지점.
- 인제스트 지연 중 사용자가 같은 챕터를 계속 수정-재저장하면 REPLACE로 계속 리셋됨 — 의도된 동작(최신 원고만 인제스트).

## 실측 기준선

- **기기**: SM-S928N (`gemma-4-E2B-it.litertlm`, GPU 백엔드)
- **날짜**: 2026-07-03
- **시나리오**: 챕터 저장(본문 2,838자) → enqueue 직후(`gateWaitMs=0`) 엔진 GPU 초기화 완료 시점(약 12.3초 지점)에 `adb shell am force-stop` → 앱 재실행 → WorkManager가 새 프로세스에서 워커를 자동 재개 → 완주.
- **첫 시도(강제 종료로 유실)**: `runIngest() chapter=0 gateWaitMs=0` → 11.3~12.3초 뒤 `Initialized LiteRT-LM engine with GPU backend` 로그까지만 도달, 그 직후 프로세스 종료로 중단. 이 시점 이후 `IngestWorker`/`IngestParser` 로그 없음 — 실제 생성(`sendMessage`) 시작 전에 죽은 것으로 판단.
- **재개 시도(성공, 새 프로세스)**:
  - `gateWaitMs`: 0ms (경합 없음, 단일 챕터)
  - `generateMs`: 80,263ms (엔진 재초기화 ~11.3초 포함 — 프로세스가 새로 뜬 것이므로 `OnDeviceEngine`도 콜드 스타트)
  - `totalMs`: 80,294ms (`generateMs`와의 차이 31ms = DB 읽기/`commitIngest` 트랜잭션 오버헤드)
  - `committed=true` (body 가드 통과, merge+스탬프 정상 커밋)
  - **WorkManager 10분(600,000ms) 한도 대비 여유**: 80,294 / 600,000 ≈ 13.4% 소진, **약 86.6% 여유**.
- **UI 확인**: 앱 재실행 직후 Analyzing 배지 정상 복원. 워커 완료 후 Wiki/Brain 탭에 엔티티(율/엘라시움/꿈/꿈 기록부/김성)와 관계가 반영됨을 화면에서 직접 확인(사용자 보고).
- **프롬프트 v2(들여쓰기 수정 후) 검증**: 이번 인제스트가 `IngestSchema.PROMPT_VERSION=2`(trimIndent 버그 수정 후) 프롬프트로 실기기에서 완주한 첫 사례. `IngestParser`가 파싱 재시도 없이 1회 생성만으로 성공 — 프롬프트 오염 제거가 파싱 실패율에 부정적 영향을 주지 않았음을 시사(표본 1건, 추세 판단에는 더 필요).
- **계측 테스트**: `IngestWorkerTest`(6/6), `StoryDatabaseMigrationTest`(1/1) 실기기 통과. 모델 필요 스모크(`OnDeviceEngineSmokeTest`, `OnDeviceEngineGpuContextTest`)는 이번 세션 초반 모델 미설치 상태에서만 실패 확인 — 모델 설치 후 재검증은 하지 않음(범위 밖).
