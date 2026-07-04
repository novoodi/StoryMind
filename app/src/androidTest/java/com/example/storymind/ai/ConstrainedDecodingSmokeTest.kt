@file:OptIn(ExperimentalApi::class)

package com.example.storymind.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spike test — NOT part of the production ingest pipeline. See docs/constrained-decoding-spike.md.
 *
 * LiteRT-LM 0.13.1 has no public "response schema" API; its only constrained-decoding surface is
 * the experimental global flag [ExperimentalFlags.enableConversationConstrainedDecoding], which is
 * read at `Engine.createConversation()` time and passed to the native layer, where the schema
 * source is the conversation's *tool descriptions* (OpenAPI function schemas). The Kotlin side
 * only proves the flag is plumbed through; whether generation is actually grammar-constrained is
 * decided inside the native .so — which is exactly what these two tests measure on-device:
 *
 * 1. [promptOnly_withConstrainedDecodingFlag_fiveRunsProduceStrictJson] — today's pipeline shape
 *    (plain prompt from [IngestSchema], no tools) with the flag on. Expected to show the flag
 *    alone does nothing for free-text responses: strict-JSON failures here mean [IngestParser]'s
 *    repairs are still required on this path.
 * 2. [toolCall_withConstrainedDecodingFlag_fiveRunsProduceSchemaShapedArguments] — the only path
 *    the flag can plausibly constrain: a single tool whose parameters mirror [IngestSchema]'s
 *    JSON schema, with `automaticToolCalling = false` so the raw tool-call arguments come back
 *    for inspection. If all runs return schema-shaped arguments, constrained decoding via tool
 *    calls is a viable replacement for prompt-and-repair.
 */
@RunWith(AndroidJUnit4::class)
class ConstrainedDecodingSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun resetExperimentalFlags() {
        // The flag is a process-wide singleton; leaking it would silently change the behavior
        // of every other conversation created in this instrumentation process.
        ExperimentalFlags.enableConversationConstrainedDecoding = false
    }

    @Test
    @Ignore(
        "Spike measurement, not a correctness gate — see docs/constrained-decoding-spike.md. It " +
            "asserts the flag DOES constrain output, which it does not on LiteRT-LM 0.13.1, so it " +
            "fails by design and would keep connectedAndroidTest red. Re-enable to re-measure on a " +
            "new model/runtime revision.",
    )
    fun promptOnly_withConstrainedDecodingFlag_fiveRunsProduceStrictJson() = runBlocking {
        val engine = OnDeviceEngine(context)
        assumeTrue("Model file not found at ${engine.modelPath} — skipping", engine.isModelAvailable)

        ExperimentalFlags.enableConversationConstrainedDecoding = true
        engine.initialize()

        val prompt = IngestSchema.buildIngestPrompt(SAMPLE_TITLE, SAMPLE_PARAGRAPHS)
        val failures = mutableListOf<String>()
        try {
            repeat(RUNS) { run ->
                val raw = engine.generate(prompt)
                val error = strictJsonError(raw)
                Log.i(TAG, "prompt-only run ${run + 1}/$RUNS: ${error ?: "valid strict JSON"}")
                if (error != null) failures += "run ${run + 1}: $error"
            }
        } finally {
            engine.release()
        }

        assertTrue(
            "Prompt-only generation with enableConversationConstrainedDecoding was NOT always " +
                "strict JSON (${failures.size}/$RUNS failed) — the flag alone does not constrain " +
                "free-text responses, IngestParser repairs remain necessary.\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    @Ignore(
        "Spike measurement, not a correctness gate — see docs/constrained-decoding-spike.md. The " +
            "tool-call path did not yield schema-shaped arguments on LiteRT-LM 0.13.1, so this " +
            "fails by design and would keep connectedAndroidTest red. Re-enable to re-measure on a " +
            "new model/runtime revision.",
    )
    fun toolCall_withConstrainedDecodingFlag_fiveRunsProduceSchemaShapedArguments() {
        val pathProbe = OnDeviceEngine(context)
        assumeTrue("Model file not found at ${pathProbe.modelPath} — skipping", pathProbe.isModelAvailable)

        ExperimentalFlags.enableConversationConstrainedDecoding = true
        val engine = createRawEngine(pathProbe.modelPath)

        val failures = mutableListOf<String>()
        try {
            repeat(RUNS) { run ->
                // Fresh conversation per run so earlier answers can't prime later ones.
                val conversation = engine.createConversation(
                    ConversationConfig(
                        tools = listOf(tool(ingestTool)),
                        // Hand back the raw tool call instead of executing it — the spike wants
                        // to inspect the model-produced arguments, not run a real tool.
                        automaticToolCalling = false,
                    )
                )
                try {
                    val message = conversation.sendMessage(TOOL_PROMPT)
                    val call = message.toolCalls.orEmpty().firstOrNull()
                    val error = when {
                        call == null ->
                            "no tool call; plain content=${message.toString().take(160)}"
                        call.name != TOOL_NAME ->
                            "unexpected tool '${call.name}'"
                        else -> schemaShapeError(call.arguments)
                    }
                    Log.i(TAG, "tool-call run ${run + 1}/$RUNS: ${error ?: "schema-shaped arguments"}")
                    if (error != null) failures += "run ${run + 1}: $error"
                } finally {
                    conversation.close()
                }
            }
        } finally {
            engine.close()
        }

        assertTrue(
            "Tool-call constrained decoding did NOT always produce schema-shaped arguments " +
                "(${failures.size}/$RUNS failed).\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /** Null when [raw] is one strict JSON object as-is — the bar constrained decoding must clear
     * for [IngestParser]'s repair regexes to become unnecessary. No repairs, no substring rescue. */
    private fun strictJsonError(raw: String): String? = try {
        JSONObject(raw.trim())
        null
    } catch (e: JSONException) {
        "${e.message}; raw starts with: ${raw.trim().take(160)}"
    }

    /** Checks the tool-call arguments have IngestSchema's top-level shape and, when entities
     * exist, the per-entity keys. Deliberately shallow — the spike question is "is the structure
     * schema-conformant", not "is the extraction good". */
    private fun schemaShapeError(arguments: Map<String, Any?>): String? {
        val missing = listOf("chapter_summary", "entities", "relations").filter { it !in arguments }
        if (missing.isNotEmpty()) return "missing keys $missing in ${arguments.keys}"
        val entities = arguments["entities"] as? List<*>
            ?: return "'entities' is not a list: ${arguments["entities"]?.javaClass}"
        if (arguments["relations"] !is List<*>) {
            return "'relations' is not a list: ${arguments["relations"]?.javaClass}"
        }
        val firstEntity = entities.firstOrNull() ?: return null
        val entityMap = firstEntity as? Map<*, *>
            ?: return "entity element is not an object: ${firstEntity.javaClass}"
        val missingEntityKeys = listOf("id", "type", "name", "desc").filter { it !in entityMap }
        return if (missingEntityKeys.isEmpty()) null
        else "entity missing keys $missingEntityKeys in ${entityMap.keys}"
    }

    /** Same GPU-then-CPU fallback as [OnDeviceEngine.initialize], on a raw [Engine] because the
     * tool test needs `createConversation(ConversationConfig)` which the wrapper doesn't expose. */
    private fun createRawEngine(modelPath: String): Engine {
        val gpuEngine = Engine(
            EngineConfig(modelPath = modelPath, backend = Backend.GPU(), maxNumTokens = MAX_NUM_TOKENS)
        )
        return try {
            gpuEngine.initialize()
            gpuEngine
        } catch (e: Exception) {
            Log.w(TAG, "GPU backend failed, falling back to CPU", e)
            try {
                gpuEngine.close()
            } catch (closeError: Exception) {
                Log.w(TAG, "Failed to clean up failed GPU engine", closeError)
            }
            Engine(
                EngineConfig(modelPath = modelPath, backend = Backend.CPU(), maxNumTokens = MAX_NUM_TOKENS)
            ).also { it.initialize() }
        }
    }

    private companion object {
        const val TAG = "ConstrainedDecodingSmokeTest"
        const val RUNS = 5

        /** Mirrors OnDeviceEngine.MAX_NUM_TOKENS (private there): headroom for manuscript + JSON. */
        const val MAX_NUM_TOKENS = 8192

        const val TOOL_NAME = "record_ingest_result"

        const val SAMPLE_TITLE = "빗속의 첫 만남"
        val SAMPLE_PARAGRAPHS = listOf(
            "지우는 폭우가 쏟아지는 골목에서 낡은 우산을 쓴 민준과 처음 마주쳤다.",
            "두 사람은 비를 피해 골목 끝의 카페 달빛으로 들어갔고, 창가 자리에 마주 앉았다.",
            "민준은 주머니에서 오래된 회중시계를 꺼내 지우에게 보여주며, 이것이 두 사람을 만나게 했다고 말했다.",
        )

        val TOOL_PROMPT = """
            아래 소설 원고를 분석해서 등장하는 엔티티(인물/장소/소품/사건)와 엔티티 사이의 관계,
            그리고 이 화의 3~4문장 요약을 추출하라. 결과는 반드시 $TOOL_NAME 도구를 호출해서 기록하라.

            화 제목: $SAMPLE_TITLE
            ${SAMPLE_PARAGRAPHS.joinToString("\n")}
        """.trimIndent()

        /** [IngestSchema]'s output schema, restated as an OpenAPI function declaration — the only
         * schema format LiteRT-LM 0.13.1 accepts (via tool descriptions; there is no response
         * schema API). Key names and the type enum match IngestSchema/IngestParser exactly so a
         * successful run proves this could feed the existing parser downstream. */
        val ingestTool = object : OpenApiTool {
            override fun getToolDescriptionJsonString(): String = """
                {
                  "name": "$TOOL_NAME",
                  "description": "소설 원고에서 추출한 위키 데이터(엔티티, 관계, 요약)를 기록한다.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "chapter_summary": {
                        "type": "string",
                        "description": "이 화의 3~4문장 요약"
                      },
                      "entities": {
                        "type": "array",
                        "description": "원고에 등장하는 엔티티 목록",
                        "items": {
                          "type": "object",
                          "properties": {
                            "id": {"type": "string", "description": "고유 식별자"},
                            "type": {"type": "string", "description": "character, place, item, event 중 하나"},
                            "name": {"type": "string", "description": "표시 이름"},
                            "desc": {"type": "string", "description": "한 줄 설명, 최소 5자"}
                          },
                          "required": ["id", "type", "name", "desc"]
                        }
                      },
                      "relations": {
                        "type": "array",
                        "description": "엔티티 사이의 관계 목록",
                        "items": {
                          "type": "object",
                          "properties": {
                            "from": {"type": "string", "description": "엔티티 id"},
                            "to": {"type": "string", "description": "엔티티 id"}
                          },
                          "required": ["from", "to"]
                        }
                      }
                    },
                    "required": ["chapter_summary", "entities", "relations"]
                  }
                }
            """.trimIndent()

            // Never invoked: automaticToolCalling = false hands the call back to the test instead.
            override fun execute(request: String): String = "{}"
        }
    }
}
