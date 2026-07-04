# 코드 감사 및 수정 기록 — 2026-07-04

StoryMind 코드베이스 전체 감사에서 발견한 버그·예외 상황·UX 문제를 심각도순으로 수정한 세션 기록. 총 **13개 커밋**, 실기기(SM-S928N, Gemma E2B 온디바이스) 검증 완료.

시작 커밋: `d59cd4e` (Fix gradlew missing the executable bit)
마지막 커밋: `ba0c420` (Add periodic auto-backup…)

---

## 검증 결과

| 게이트 | 결과 |
|---|---|
| `./gradlew test` (JVM 단위 테스트) | ✅ 통과 |
| `:core:compileKotlinJvm` (규칙 4 컴파일러 게이트) | ✅ 통과 |
| `grep -rn "^import android\." core/src/commonMain` (규칙 4) | ✅ 0건 |
| `:app:assembleDebug` | ✅ 통과 |
| **`connectedAndroidTest` (전체, 실기기)** | ✅ **32개 실행, 0 실패, 2 무시** (54m 16s) |

무시 2건은 `ConstrainedDecodingSmokeTest`의 스파이크 측정용 테스트로, 이번에 `@Ignore` 처리했다(아래 #5a 참고).

이번 세션에서 **테스트가 잡아낸 실제 프로덕션 버그 2건**(스테일 린트 부활, 테스트 격리 크래시)도 함께 수정했다.

---

## 🔴 High — 정상 플로우에서 사용자가 겪는 문제

### 1. 재저장 시 파생 데이터 중복/오염 (`578872d`, `85be55e`)
**증상:** "저장 → 인제스트 완료 → 이어서 더 쓰고 → 다시 저장"이라는 가장 흔한 집필 흐름에서, 그 화의 설명 줄(`3화: …`)이 위키 모든 엔티티에 두 번씩 쌓였다. `merge`가 같은 화의 기존 줄을 교체하지 않고 무조건 덧붙였기 때문.

**수정:**
- `ChapterProgress.merge`가 화 라벨을 명시 파라미터로 받아, 그 화의 desc 줄을 **모든 엔티티에서 먼저 걷어낸 뒤** 새 결과에 있는 것만 다시 붙인다.
- 그 결과 재인제스트가 더는 언급하지 않는 엔티티는 마지막 줄이 사라지며 **노드까지 삭제**되고, 제거된 노드로 **끊긴 엣지도 필터**된다.
- 모델이 desc에 개행을 섞어도 한 줄로 평탄화 — 린트 이력 누출(자기확인) 방지.
- 첫 전진 인제스트는 아무것도 스트립하지 않아 **재구축 결정성 유지**.

**남은 한계:** 양 끝이 다 살아있는데 이 화만 만든 엣지 하나는 엣지 provenance가 없어 남는다 → 전체 재구축이 정리(rule 1). JVM 테스트 5건.

### 2. AI 분석 중 저장 버튼 잠김 → 원고 유실 위험 (`85d13bf`)
**증상:** 인제스트(화당 수 분)·재구축(수십 분) 동안 저장 버튼이 스피너로 잠겨, 그 사이 쓴 원고를 저장 못 한 채 프로세스가 죽으면 유실. 규칙 3("원고 저장이 인제스트에 종속되면 안 된다")의 정신을 UI가 깨고 있었다.

**수정:** `EditorScreen`에서 `saving = aiStatus == Analyzing` 결합 제거. 저장은 밀리초짜리 Room 쓰기라 잠글 이유가 없고, 진행 중 인제스트와의 충돌은 워커 REPLACE + `commitIngest`의 body 가드가 이미 흡수. 프로세스 재시작 후 저장된 draft의 `canAdvance`도 복원.

### 3. 파서 수리 정규식이 정상 JSON 훼손 (`d023617`)
**증상:** Gemma의 JSON 실수를 고치는 수리 정규식이 **정상 응답에도 무조건** 돌았다. `MISSING_VALUE_OPEN_QUOTE_REGEX`는 `:` 뒤 공백 없는 문자에 발화 → desc 속 `3:00` 같은 시간·점수 표기를 만나면 유효 JSON을 깨뜨려, 그 화는 재시도 3번이 전부 같은 이유로 실패하고 위키가 영영 안 만들어질 수 있었다. (실행으로 재현 확인.)

**수정:** `IngestParser`·`LintParser` 모두 **strict 파싱을 먼저 시도**하고 실패한 출력만 수리 체인을 태운다. 수리 대상 패턴은 전부 strict-invalid라 수리 커버리지는 그대로. 회귀 테스트(`"3:00"` 콜론, 대화문 따옴표) 추가.

---

## 🟠 Medium — 특정 상황에서 크래시·복구 불능

### 4. 백업 복원 검증 허술 → 매 실행 크래시 루프 (`361e020`)
**증상:** 정적 검사(매직 바이트·integrity·`chapters` 테이블·버전 상한)만으로는 "chapters 테이블만 있는 다른 SQLite 파일"이 통과했다. 복원 후 다음 실행 첫 쿼리에서 Room이 identity-hash 불일치로 throw → pending이 이미 소비돼 자동 복구 없는 크래시 루프.

**수정:**
- 복원 확정 전에 **버리는 사본으로 실제 Room을 열어봐서**(마이그레이션+구조 검증을 미리 겪음) 실패 시 새 사유 `SCHEMA_MISMATCH`로 거부.
- 마이그레이션 체인이 두 경로로 갈라지지 않게 `StoryDatabase.build()` 공용 빌더로 통합.
- 파생 테이블 enum 매핑(`SmBadgeType.valueOf`)을 안전 매핑으로 — 이상 값이 크래시 대신 기본값(재구축으로 정정).
- 기기 테스트 `RestoreValidationTest`(이물 SQLite 거부 / 진짜 백업 통과).

### 5. "임시 보관됩니다" 약속에 실체 없음 (`361e020`)
**증상:** 복원 확인 시트가 pre-restore 자동 백업을 약속하지만, `pre-restore.db`는 쓰기만 있고 읽는 코드가 없어 잘못 복원한 사용자가 앱 안에서 되돌릴 수 없었다(adb 필요).

**수정:** 설정에 "복원 전 데이터로 되돌리기" 행 추가(파일 존재 시에만). 기존 검증→확인→재시작 경로를 그대로 재사용하고, 확정 시 현재 상태가 새 pre-restore로 맞교환되어 다시 앞으로도 갈 수 있다.

### 6. 브레인 그래프 노드 겹침 + 드래그 위치 증발 (`2acc387`, `4e37b0c`)
**증상:** ① 화마다 새 노드가 같은 원 위 같은 자리에 포개지고, 1개짜리 화는 전부 정중앙(50,50)에 쌓였다. ② 노드를 드래그해도 탭만 바꾸면 원위치(로컬 상태만 있고 DB 저장 없음).

**수정:**
- `layoutNodes`를 전역 누적 인덱스 기반 **황금각 나선** 배치로 교체(포개짐 해소, 증분/재구축 좌표 일치 — 테스트로 고정).
- DAO에 좌표만 겨냥한 `updateNodePosition` 추가, 드래그 종료 시 DB 저장 + 로컬 동기화.

---

## 🟡 Low / UX 정리

### 무동작 UI 정리 (`56983d6`)
- 설정 토글들이 `remember { mutableStateOf }`라 탭 전환에도 초기화됐다 → **SharedPreferences 기반 `SettingsRepository`**로 영속화.
- **자동 분석** 토글을 실제 동작에 연결: OFF면 저장 시 인제스트를 건너뛴다(원고 저장·`canAdvance`는 유지, 규칙 3).
- **맞춤법 검사**를 에디터 `KeyboardOptions.autoCorrectEnabled`로 연결.
- 백킹 없는 UI 제거: "설정 충돌 알림"·"위키 자동 생성" 토글, 에디터 B/I/링크 버튼, 위키 검색 아이콘.

### 미분석 화 배너 (`c69fdfc`)
- 자동 분석 OFF/모델 부재로 쌓인 미인제스트 화를 `pendingAnalysisCount`(chapters 테이블에서 직접 계산)로 노출.
- 에디터에 "분석 안 된 화 n개 [지금 분석]" 배너 — 분석/재구축 중, 실패(Warning) 중, 모델 없을 땐 숨김.
- 액션은 `retryIngest`와 같은 **비파괴 resume**(전체 재구축과 달리 이미 된 화는 안 건드림).

### 에디터 성능 + 화 제목 입력 (`f941cad`)
- 이전 화 전체를 non-lazy `Column`으로 매 키입력마다 재구성하던 것을 **`LazyColumn`**으로(화면 밖 화는 컴포즈 안 됨).
- 저장 경로가 늘 `title=null`이던 걸 **실제 제목 입력 필드**로 연결. `currentTitle`이 저장·진행·재시작 복원까지 유지.

### 주기적 자동 백업 (`ba0c420`)
- `AutoBackupWorker`가 하루 주기로 VACUUM INTO 스냅샷을 내부 저장소에 롤링 보관(최신 3개), 배터리 여유 조건, KEEP로 재실행마다 초기화 방지.
- 설정에 "최근 자동 백업에서 복원"(스냅샷 있을 때만), 기존 하드닝된 검증/확인/승격 경로 재사용.
- `RestoreReady`의 boolean을 `RestoreSource` enum(External/PreRestore/AutoBackup)으로 일반화.
- 기기 테스트 `AutoBackupTest`(스냅샷 생성→검증→복원 스테이징).

### GPU 폴백 (`54cb388`)
GPU 초기화 폴백을 `catch(Exception)`에서 `catch(Throwable)`로 — libOpenCL 없는 기기의 `UnsatisfiedLinkError` 같은 Error 계열 실패도 CPU 폴백으로 흡수(폴백이 가장 필요한 기기에서 크래시하던 경로).

---

## 테스트가 잡아낸 프로덕션 버그 (수정 완료)

- **스테일 린트 결과 부활** (`c69fdfc`에 포함): 화를 재저장해 이전 린트 결과를 무효화해도, `filterNotNull`이 null 워치를 삼켜 옛 WorkInfo 구독이 안 끊겼다. 재저장의 IngestWorker enqueue만으로 끝난 린트의 SUCCEEDED가 재방출되어 방금 리셋한 Idle이 스테일 Done으로 되덮였다. `observeLintWork`가 null을 실제 전환으로 처리해 구독을 끊도록 수정.
- **테스트 격리 크래시**: ViewModel 테스트 teardown이 `viewModelScope.cancel()`(비동기) 직후 DB를 닫으면 아직 정리 중인 Room Flow 관찰자가 닫힌 커넥션 풀을 건드려 백그라운드 크래시 → 다음 테스트 실패. scope Job을 `join()`으로 기다린 뒤 닫도록 강화.

---

## 남은 항목 (미착수)

- **재저장 화의 스테일 엣지 완전 해결**: 양 끝이 다 살아있는데 한 화만 만든 엣지는 엣지 단위 provenance(스키마 변경 — 규칙 6 마이그레이션 규모)가 필요. 현재는 전체 재구축이 커버.
- **생성 계열 스모크 테스트의 측정 성격**: `ConstrainedDecodingSmokeTest`는 스파이크 결론 재확인용이라 `@Ignore` 유지. 새 모델/런타임에서 재측정하려면 해제.
- 기타: 에디터 이전 화 렌더의 화별 lazy화(현재는 화 단위 item), 화 제목 편집 UX 세부.

---

## 관련 파일

**`:core` (도메인 로직)**
- `ai/ChapterProgress.kt` — merge 스테일 정리 + 개행 평탄화
- `ai/IngestParser.kt`, `ai/LintParser.kt` — strict-first 파싱
- `ai/NodeLayout.kt` — 황금각 나선 배치

**`:app`**
- `ui/StoryViewModel.kt` — 자동 분석 게이트, 미분석 카운트, 제목 상태, 복원 소스, 린트 워치 수정
- `ui/screens/EditorScreen.kt` — LazyColumn, 제목 필드, 저장 잠금 해제, 미분석 배너
- `ui/screens/SettingsScreen.kt` — 토글 정리, 복원/자동백업 진입점
- `data/SettingsRepository.kt` — 설정 영속화 (신규)
- `data/backup/StoryBackupManager.kt`, `BackupValidator.kt`, `db/StoryDatabase.kt` — 복원 검증 강화, 자동 백업
- `work/AutoBackupWorker.kt` — 주기 백업 (신규)
- `data/db/StoryDao.kt`, `StoryEntities.kt` — 노드 위치 저장, 안전 enum 매핑
- `ai/OnDeviceEngine.kt` — GPU 폴백
