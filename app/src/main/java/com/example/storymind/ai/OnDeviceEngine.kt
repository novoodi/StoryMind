package com.example.storymind.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Which backend the active [Engine] initialized with — surfaced so callers (e.g.
 * [com.example.storymind.work.IngestWorker]'s timing log) can tell a slow generation apart from
 * a CPU-fallback generation instead of just a slow one. */
enum class EngineBackend { GPU, CPU }

/**
 * Thin wrapper around the LiteRT-LM engine: raw string prompt in, raw string response out.
 * Prompt formatting, thinking-mode tokens, and response parsing belong to the caller (Ingest layer).
 */
class OnDeviceEngine(context: Context) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var backend: EngineBackend? = null

    val modelPath: String
        get() = File(appContext.getExternalFilesDir(MODELS_DIR_NAME), MODEL_FILENAME).absolutePath

    val isModelAvailable: Boolean
        get() = File(modelPath).exists()

    /** Null before the first successful [initialize] and after [release]. */
    val activeBackend: EngineBackend?
        get() = backend

    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (engine != null) return@withLock

            check(isModelAvailable) { "Model file not found at $modelPath" }

            val gpuEngine = Engine(
                EngineConfig(modelPath = modelPath, backend = Backend.GPU(), maxNumTokens = MAX_NUM_TOKENS)
            )
            try {
                gpuEngine.initialize()
                engine = gpuEngine
                backend = EngineBackend.GPU
                Log.i(TAG, "Initialized LiteRT-LM engine with GPU backend")
                return@withLock
            } catch (e: Throwable) {
                // Throwable(Exception 아님): GPU 초기화는 네이티브 라이브러리 로딩을 수반해
                // libOpenCL이 없는 기기에선 UnsatisfiedLinkError 같은 Error 계열로 실패할 수
                // 있다 — GPU가 안 되는 기기야말로 CPU 폴백이 있어야 하는 기기이므로, 여기서
                // Error를 흘려보내면 폴백이 가장 필요한 곳에서 크래시가 난다.
                Log.w(TAG, "GPU backend initialization failed, falling back to CPU", e)
                try {
                    gpuEngine.close()
                } catch (closeError: Exception) {
                    Log.w(TAG, "Failed to clean up failed GPU engine", closeError)
                }
            }

            val cpuEngine = Engine(
                EngineConfig(modelPath = modelPath, backend = Backend.CPU(), maxNumTokens = MAX_NUM_TOKENS)
            )
            try {
                cpuEngine.initialize()
                engine = cpuEngine
                backend = EngineBackend.CPU
                Log.i(TAG, "Initialized LiteRT-LM engine with CPU backend")
            } catch (e: Exception) {
                engine = null
                backend = null
                throw e
            }
        }
    }

    /**
     * [sampler] is null by default, matching this method's behavior before ingest's retry
     * escalation was added: `createConversation(ConversationConfig())` leaves `samplerConfig`
     * null, which — per a bytecode read of `litertlm-android-0.13.1`'s `SamplerConfig`/
     * `ConversationConfig` classes (no public API exposes it) — falls through to whatever
     * sampler the native engine defaults to internally; that default isn't introspectable from
     * the Kotlin side. Only [IngestService] passes a non-null [sampler] today; [LintService]
     * (via [com.example.storymind.platform.IngestEngineProvider.withTextEngine]) still always
     * calls this with the default, so its sampling is unchanged by ingest's escalation.
     */
    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    suspend fun generate(prompt: String, sampler: SamplerSettings? = null): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val activeEngine =
                checkNotNull(engine) { "Engine is not initialized. Call initialize() first." }
            val conversation = activeEngine.createConversation(
                ConversationConfig(samplerConfig = sampler?.toLiteRtSamplerConfig())
            )
            try {
                val wallClockStart = System.currentTimeMillis()
                val message = conversation.sendMessage(prompt)
                val wallClockMs = System.currentTimeMillis() - wallClockStart
                // Benchmark info requires BenchmarkParams in EngineSettings, which this app
                // doesn't set; querying it throws and must not take down a successful generation.
                val benchmark = try {
                    conversation.getBenchmarkInfo()
                } catch (e: Exception) {
                    Log.d(TAG, "generate() benchmark info unavailable: ${e.message}")
                    null
                }
                Log.d(
                    TAG,
                    "generate() wallClockMs=$wallClockMs " +
                        "timeToFirstTokenS=${benchmark?.timeToFirstTokenInSecond} " +
                        "prefillTokens=${benchmark?.lastPrefillTokenCount} " +
                        "prefillTokPerS=${benchmark?.lastPrefillTokensPerSecond} " +
                        "decodeTokens=${benchmark?.lastDecodeTokenCount} " +
                        "decodeTokPerS=${benchmark?.lastDecodeTokensPerSecond}",
                )
                val contentsText = message.toString()
                Log.d(TAG, "generate() channels=${message.channels.keys} contentsLength=${contentsText.length}")
                // Some Gemma builds stream the answer into named channels instead of the
                // plain content parts this wrapper's toString() reads; fall back to those.
                contentsText.ifBlank { message.channels.values.joinToString("\n") }
            } finally {
                conversation.close()
            }
        }
    }

    suspend fun release() = withContext(Dispatchers.IO) {
        mutex.withLock {
            engine?.close()
            engine = null
            backend = null
        }
    }

    private fun SamplerSettings.toLiteRtSamplerConfig() =
        SamplerConfig(topK = topK, topP = topP, temperature = temperature, seed = seed)

    companion object {
        private const val TAG = "OnDeviceEngine"
        private const val MODELS_DIR_NAME = "models"
        const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"

        /**
         * Default engine context window is too small to hold a full chapter's manuscript plus
         * a multi-entity JSON response, silently truncating generation mid-array. Raised to give
         * the ingest prompt (long Korean manuscript + schema instructions + response) headroom.
         */
        private const val MAX_NUM_TOKENS = 8192
    }
}