package com.example.storymind.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around the LiteRT-LM engine: raw string prompt in, raw string response out.
 * Prompt formatting, thinking-mode tokens, and response parsing belong to the caller (Ingest layer).
 */
class OnDeviceEngine(context: Context) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile
    private var engine: Engine? = null

    val modelPath: String
        get() = File(appContext.getExternalFilesDir(MODELS_DIR_NAME), MODEL_FILENAME).absolutePath

    val isModelAvailable: Boolean
        get() = File(modelPath).exists()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (engine != null) return@withLock

            check(isModelAvailable) { "Model file not found at $modelPath" }

            val gpuEngine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.GPU()))
            try {
                gpuEngine.initialize()
                engine = gpuEngine
                Log.i(TAG, "Initialized LiteRT-LM engine with GPU backend")
                return@withLock
            } catch (e: Exception) {
                Log.w(TAG, "GPU backend initialization failed, falling back to CPU", e)
                try {
                    gpuEngine.close()
                } catch (closeError: Exception) {
                    Log.w(TAG, "Failed to clean up failed GPU engine", closeError)
                }
            }

            val cpuEngine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU()))
            try {
                cpuEngine.initialize()
                engine = cpuEngine
                Log.i(TAG, "Initialized LiteRT-LM engine with CPU backend")
            } catch (e: Exception) {
                engine = null
                throw e
            }
        }
    }

    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val activeEngine =
                checkNotNull(engine) { "Engine is not initialized. Call initialize() first." }
            val conversation = activeEngine.createConversation()
            try {
                conversation.sendMessage(prompt).toString()
            } finally {
                conversation.close()
            }
        }
    }

    suspend fun release() = withContext(Dispatchers.IO) {
        mutex.withLock {
            engine?.close()
            engine = null
        }
    }

    companion object {
        private const val TAG = "OnDeviceEngine"
        private const val MODELS_DIR_NAME = "models"
        const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
    }
}