package com.example.storymind.ai

/**
 * ai/'s own mirror of the four knobs `com.google.ai.edge.litertlm.SamplerConfig` exposes
 * (topK/topP/temperature/seed). A separate domain type — not the vendor class itself — because
 * [OnDeviceEngine] is the only place in ai/ allowed to touch LiteRT-LM types directly (CLAUDE.md
 * rule 4); [IngestService]'s retry-escalation policy needs to specify sampler settings without
 * importing that boundary. [OnDeviceEngine.generate] translates this into the real
 * `SamplerConfig` right before calling into the engine.
 */
data class SamplerSettings(val topK: Int, val topP: Double, val temperature: Double, val seed: Int)
