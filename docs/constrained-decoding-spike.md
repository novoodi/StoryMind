# 스파이크: LiteRT-LM 0.13.1의 constrained decoding(JSON 스키마 강제 출력) 지원 조사

- 날짜: 2026-07-02
- 조사 대상: `com.google.ai.edge.litertlm:litertlm-android:0.13.1` (gradle/libs.versions.toml의 `litertlm = "0.13.1"`)
- 조사 방법: 로컬 Gradle 캐시의 AAR(`~/.gradle/caches/.../litertlm-android-0.13.1.aar`)에서 `classes.jar`를 추출해 `javap -p`/`javap -c`로 공개 API와 바이트코드, Kotlin 메타데이터를 확인. 네이티브(.so) 내부 동작은 이 방법으로는 확인 불가.

## 결론 (TL;DR)

**응답 JSON 스키마를 직접 지정하는 공개 API는 없다.** 다만 실험적 전역 플래그
`ExperimentalFlags.enableConversationConstrainedDecoding`이 존재하며, 이 플래그는
**tool calling 경로에 묶여 있다** — 스키마의 출처가 응답 포맷 옵션이 아니라 대화에 등록된
툴 설명(OpenAPI function schema)이기 때문이다. Kotlin 레이어에서 확인 가능한 것은 플래그가
`nativeCreateConversation`으로 전달된다는 사실까지이고, 실제로 생성이 문법 제약되는지는
네이티브 구현 안에 있어 **기기 테스트로만 검증 가능**하다. 이를 위해
`ConstrainedDecodingSmokeTest`를 준비했다 (아래 참고).

## 확인한 API 표면

| API | 확인 내용 | 스키마/포맷 제약 옵션 |
|---|---|---|
| `EngineConfig` | `modelPath`, `backend`, `visionBackend`, `audioBackend`, `maxNumTokens`, `maxNumImages`, `cacheDir` | 없음 |
| `ConversationConfig` | `systemInstruction`, `initialMessages`, `tools`, `samplerConfig`, `automaticToolCalling`, `channels`, `extraContext`, `loraConfig` | 직접 옵션 없음 — `tools`가 유일한 스키마 통로 |
| `SessionConfig` | `samplerConfig`, `loraConfig` | 없음 |
| `SamplerConfig` | `topK`, `topP`, `temperature`, `seed` | 없음 |
| `Conversation.sendMessage(message/contents/text, extraContext)` | `extraContext: Map<String, Any>`는 JSON으로 직렬화되어 네이티브로 전달되는 프롬프트 템플릿용 부가 컨텍스트 | 응답 포맷 파라미터 없음 |
| `ExperimentalFlags` | `enableBenchmark`, `enableSpeculativeDecoding`, **`enableConversationConstrainedDecoding`**, `convertCamelToSnakeCaseInToolDescription`, `filterChannelContentFromKvCache`, `overwritePromptTemplate`, `visualTokenBudget` | **constrained decoding 플래그 존재 (전역, 실험적)** |
| `Capabilities` | `hasSpeculativeDecodingSupport()`만 존재 | constrained decoding 지원 여부 질의 API 없음 |

바이트코드로 확인한 흐름:

- `Engine.createConversation()`이 호출될 때 `ExperimentalFlags.enableConversationConstrainedDecoding`
  값을 읽어 `LiteRtLmJni.nativeCreateConversation(..., boolean, ...)`의 인자로 넘긴다.
  즉 플래그는 **대화 생성 시점**에 고정되고, 메시지 단위로는 제어할 수 없다.
- `nativeCreateConversation`에 함께 넘어가는 문자열은 초기 메시지(시스템 지시 포함) JSON,
  **툴 설명 JSON**(`ToolManager.getToolsDescription()`), 채널 JSON, extraContext JSON뿐이다.
  응답 스키마에 해당하는 별도 인자는 없다 → constrained decoding이 참조할 수 있는 스키마는
  툴 설명이 유일하다.
- 툴 설명 포맷은 OpenAPI function 스타일이다 (`ReflectionTool.getToolDescription` 기준):
  `{"name", "description", "parameters": {"type": "object", "properties", "required"}}`,
  프로퍼티 타입은 `string`/`integer`/`boolean`/`number`/`array`(+`items`)/`object`, `nullable` 지원.
  툴 정의 방법은 두 가지: `tool(ToolSet)`(`@Tool`/`@ToolParam` 어노테이션 + 리플렉션) 또는
  `tool(OpenApiTool)`(스키마 JSON 문자열 직접 제공 — `fun getToolDescriptionJsonString()`, `fun execute(String)`).
- `automaticToolCalling = false`이면 모델이 만든 tool call이 실행되지 않고
  `Message.toolCalls`(`List<ToolCall>`, `name` + `arguments: Map<String, Any>`)로 반환된다 —
  즉 스키마 제약된 인자를 우리가 직접 받아 파싱할 수 있다.

## 무엇이 없었나

- `EngineConfig`/`ConversationConfig`/`SessionConfig`/`sendMessage` 어디에도
  `responseSchema`, `responseFormat`, `jsonMode` 류의 파라미터 없음.
- 스키마를 받는 클래스(예: `ConstraintOptions`) 자체가 AAR에 존재하지 않음.
- 일반 텍스트 응답(툴 없이 프롬프트만)의 출력 형식을 제약하는 수단 없음 —
  플래그를 켜도 스키마 출처가 없으므로 이 경로에는 효과가 없을 것으로 추정 (기기 검증 필요).
- 참고: 캐시에 있던 0.11.0에는 존재 여부를 별도 확인하지 않았다. 프로젝트는 0.13.1 고정.

## 기기 검증: ConstrainedDecodingSmokeTest

`app/src/androidTest/java/com/example/storymind/ai/ConstrainedDecodingSmokeTest.kt`.
모델 파일(`gemma-4-E2B-it.litertlm`)이 없으면 `Assume`으로 skip. 실행:

```bash
./gradlew connectedAndroidTest
# 또는 이 클래스만:
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.storymind.ai.ConstrainedDecodingSmokeTest
```

| 테스트 | 경로 | 5회 반복 검증 내용 | 해석 |
|---|---|---|---|
| `promptOnly_withConstrainedDecodingFlag_fiveRunsProduceStrictJson` | 현행 파이프라인 형태: `IngestSchema.buildIngestPrompt` + 플래그 on, 툴 없음 | 원시 응답이 수리 없이 `JSONObject`로 곧장 파싱되는가 | 실패가 있으면 플래그 단독으로는 효과 없음이 확정 → `IngestParser` 수리 유지 |
| `toolCall_withConstrainedDecodingFlag_fiveRunsProduceSchemaShapedArguments` | `IngestSchema` 스키마를 OpenAPI function으로 옮긴 툴 1개 + `automaticToolCalling = false` + 플래그 on | `toolCalls[0].arguments`가 스키마 형태(`chapter_summary`/`entities`/`relations`, 엔티티 4키)인가 | 5/5 성공이면 툴콜 경유 constrained decoding이 프롬프트+수리 방식의 대체 후보 |

### 온디바이스 실측 결과 (2026-07-02, SM-S928N, `gemma-4-E2B-it.litertlm`)

```
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.storymind.ai.ConstrainedDecodingSmokeTest
```

두 테스트 모두 **5/5 실패** — 두 경로 다 constrained decoding이 스키마를 강제하지 않음을 실기기에서 확인:

- `promptOnly_...`: 5회 전부 `` ```json `` 마크다운 코드펜스로 응답을 감싸서 반환 (플래그를 켠 상태에서도). 프롬프트가 코드펜스를 명시적으로 금지하는데도 발생 — 플래그 단독은 자유 텍스트 응답에 아무 제약을 걸지 않는다는 가설을 그대로 확인.
- `toolCall_...`: 5회 전부 `entities[]` 각 원소에서 `type` 키가 누락됨 (`id`/`name`/`desc`만 존재). `type`이 JSON Schema의 예약어(`"type": "object"` 등)와 이름이 겹치는 프로퍼티라서, 툴콜 인자 생성 시 스키마의 `properties.type`이 스키마 자체의 `type` 키워드와 혼동되는 것으로 보인다 (원인 확정을 위한 추가 조사는 이 스파이크 범위 밖). 결과적으로 툴콜 경로도 오늘 스키마 그대로는 신뢰할 수 없다.

**결론 확정**: 두 경로 모두 실패했으므로 constrained decoding은 당분간 도입하지 않는다.
`IngestParser`의 복구 정규식 + `IngestService`의 3회 재생성이 계속 정식 경로다 — 지우거나
우회하지 말 것.

## 대안 (툴콜 경로도 실패할 경우)

1. **현행 유지** — `IngestSchema` 프롬프트 + `IngestParser`의 수리 정규식 + `IngestService`의
   3회 재생성. 이미 알려진 Gemma 오류 패턴들을 커버하고 있고, 파싱 실패는 원고 저장과
   분리되어 있어(불변 규칙 3) 치명적이지 않다.
2. **샘플링 파라미터 조정** — `ConversationConfig.samplerConfig`(topK/topP/temperature)로
   temperature를 낮춰 JSON 슬립 빈도를 줄이는 실험. 스키마 보장은 아니지만 저비용.
3. **라이브러리 버전 추적** — constrained decoding 플래그가 실험 단계로 존재한다는 것은
   응답 스키마 API가 후속 버전에 공개될 가능성이 있다는 뜻. `litertlm` 버전 업데이트 시
   `ExperimentalFlags`와 config 클래스들을 재확인할 것.
4. **다른 온디바이스 러너 검토** — 스키마 강제가 필수 요구가 되면 LiteRT-LM 외의 러너
   (예: MediaPipe LLM Inference API 등)의 constrained decoding 지원 여부를 별도 스파이크로
   조사. (이번 조사 범위 밖 — 지원 여부 미확인.)
