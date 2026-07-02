package com.example.storymind.platform

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.storymind.ai.EngineBackend
import com.example.storymind.ai.OnDeviceEngine
import com.example.storymind.ai.OnDeviceTextEngine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * App-scoped owner of the on-device engine. The engine used to belong to StoryViewModel, which
 * forced a `runBlocking { engine.release() }` in `onCleared` and tied the engine's life to the
 * UI; ingest now runs in a WorkManager worker that can outlive (or predate) any ViewModel, so
 * ownership has to sit at process scope.
 *
 * Lives in platform/ (not ai/) because it is an Android-lifecycle concern — ai/ stays free of
 * new Android dependencies per CLAUDE.md rule 4; this object only *consumes* ai/ types.
 */
object IngestEngineProvider {

    /**
     * Serializes entire ingest runs (context read → generation → commit), not just generation.
     * Unique work names are per chapter, so WorkManager may start chapter n+1's worker while
     * chapter n's is still running; without this gate the second worker would read `existingWiki`
     * before the first one's merge commits and the model would mint duplicate ids for entities
     * chapter n just established. A process-wide Mutex is enough because WorkManager runs this
     * app's workers in-process.
     */
    val ingestGate = Mutex()

    /** Guards [refCount]/release pairing. Separate from [ingestGate] so UI-triggered engine use
     * (if any is added later) doesn't have to queue behind a multi-minute ingest run. */
    private val lifecycle = Mutex()

    private var engine: OnDeviceEngine? = null
    private var refCount = 0

    /**
     * Test seam: replaces the whole native path so IngestWorkerTest can exercise the worker
     * end-to-end without the model file on the test device. Reset to null in test teardown.
     */
    @VisibleForTesting
    var textEngineOverride: OnDeviceTextEngine? = null

    fun isModelAvailable(context: Context): Boolean =
        textEngineOverride != null || engineFor(context).isModelAvailable

    /**
     * Runs [block] against the shared engine with refcounted lifetime: the native engine is
     * initialized on first use and released when the last user leaves. Gemma E2B holds GBs of
     * native memory, so idling with the model loaded is not acceptable; the cost is a re-load
     * (seconds) when back-to-back ingests don't overlap — cheap next to a multi-minute ingest.
     *
     * [block] also receives the [EngineBackend] that ended up active, so a caller can log it
     * alongside generation time — querying [OnDeviceEngine.activeBackend] *after* this call
     * returns is unsafe, since a refcount drop to zero in the `finally` below clears it via
     * [OnDeviceEngine.release]. Null under [textEngineOverride] (tests don't have a real backend).
     */
    suspend fun <T> withTextEngine(context: Context, block: suspend (OnDeviceTextEngine, EngineBackend?) -> T): T {
        textEngineOverride?.let { return block(it, null) }

        val engine = engineFor(context)
        lifecycle.withLock { refCount++ }
        try {
            // Idempotent, and re-creates the native engine after a refcount-zero release.
            engine.initialize()
            return block(OnDeviceTextEngine(engine::generate), engine.activeBackend)
        } finally {
            // NonCancellable: a cancelled worker (e.g. same-chapter re-save REPLACEd it) must
            // still balance the refcount and free native memory, and suspending calls in a
            // cancelled coroutine's finally would otherwise throw immediately.
            withContext(NonCancellable) {
                lifecycle.withLock {
                    refCount--
                    if (refCount == 0) engine.release()
                }
            }
        }
    }

    /** The wrapper itself is cheap (no native load until initialize()), so one instance is kept
     * for the process and initialize/release cycle the native engine inside it. */
    private fun engineFor(context: Context): OnDeviceEngine = synchronized(this) {
        engine ?: OnDeviceEngine(context.applicationContext).also { engine = it }
    }
}
