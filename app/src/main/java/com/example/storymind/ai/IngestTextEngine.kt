package com.example.storymind.ai

/**
 * Like [OnDeviceTextEngine] but every call carries the [SamplerSettings] [IngestService] wants
 * for that specific retry attempt (see its escalation policy in `generateParsed`). A separate
 * interface — rather than adding a sampler parameter to [OnDeviceTextEngine] — because
 * [OnDeviceTextEngine] is shared with [LintService], and lint's engine calls must stay on
 * whatever sampler the engine defaults to: lint is thinking-mode reasoning, and whether low
 * temperature helps it hasn't been measured the way ingest's has (docs/constrained-decoding-spike.md,
 * "대안 2"), so this change doesn't touch it. [com.example.storymind.platform.IngestEngineProvider]
 * is the only place that constructs a real one; tests substitute a lambda the same way they do
 * for [OnDeviceTextEngine].
 */
fun interface IngestTextEngine {
    suspend fun generate(prompt: String, sampler: SamplerSettings): String
}
