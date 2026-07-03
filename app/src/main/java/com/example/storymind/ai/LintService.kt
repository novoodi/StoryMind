package com.example.storymind.ai

/**
 * One entity's wiki history handed to [LintService.lint] for consistency checking.
 *
 * [descHistory] is deliberately a plain list rather than a raw [com.example.storymind.data.WikiEntry.desc]
 * string: that field is [ChapterProgress.merge]'s own accumulation format, and coupling this
 * service to how it's punctuated would break the moment that format changes. The caller (out of
 * this v1's scope — no UI/DB wiring yet) is expected to split it into per-chapter entries, oldest
 * first, same content [ChapterProgress.merge] already writes (chapter-labeled, e.g. `"1화:
 * 물을 무서워한다"`) so [LintSchema.buildLintPrompt] can present them as a timeline.
 */
data class LintCandidate(
    val entityId: String,
    val entityName: String,
    val descHistory: List<String>,
)

/**
 * Result of one lint pass. [succeeded] exists so a caller can't mistake "the model failed to
 * produce parseable JSON after every retry" for "the model checked and found nothing wrong" —
 * both look like an empty [findings] list otherwise, and only one of them is safe to treat as a
 * clean bill of health.
 */
data class LintResult(
    val findings: List<LintFinding>,
    val succeeded: Boolean,
)

/**
 * Checks whether this chapter's manuscript stays consistent with entities' established wiki
 * history. Sibling of [IngestService] — same [OnDeviceTextEngine] seam for fake-engine testing,
 * same retry-on-parse-failure shape — but a separate pipeline: v1 only returns [LintResult] to
 * its caller. No DB writes, no UI wiring (CLAUDE.md rule 6 doesn't apply — nothing new is
 * persisted), and the existing ingest pipeline is untouched.
 */
class LintService(private val engine: OnDeviceTextEngine) {

    /**
     * Entities with fewer than [MIN_HISTORY_FOR_LINT] history entries have nothing to contrast
     * this chapter's portrayal against, so they're filtered out before spending an engine call —
     * if that leaves nothing to check, [lint] returns immediately without generating at all.
     */
    suspend fun lint(
        chapterLabel: String,
        paragraphs: List<String>,
        candidates: List<LintCandidate>,
    ): LintResult {
        val eligible = candidates.filter { it.descHistory.size >= MIN_HISTORY_FOR_LINT }
        if (eligible.isEmpty()) return LintResult(findings = emptyList(), succeeded = true)

        val prompt = LintSchema.buildLintPrompt(chapterLabel, paragraphs, eligible)
        val findings = generateFindings(prompt)
            ?: return LintResult(findings = emptyList(), succeeded = false)
        return LintResult(findings = findings, succeeded = true)
    }

    /**
     * Unlike [IngestService], a final parse failure here returns null instead of
     * falling back to an empty-but-"successful" result: [lint] needs to tell that failure apart
     * from a genuine "nothing wrong" verdict, which an ingest caller never had to distinguish
     * (a chapter with truly no entities is legitimate; a lint pass that never produced usable
     * JSON is not the same as one that ran and found nothing).
     */
    private suspend fun generateFindings(prompt: String): List<LintFinding>? {
        var raw = ""
        repeat(MAX_ATTEMPTS) { attempt ->
            raw = engine.generate(prompt)
            LintParser.parseOrNull(raw)?.let { return it }
            val attemptsLeft = MAX_ATTEMPTS - attempt - 1
            ingestLogger.w(TAG, "Lint JSON parse failed (attempt ${attempt + 1}/$MAX_ATTEMPTS), $attemptsLeft retries left")
        }
        ingestLogger.w(TAG, "Lint gave up after $MAX_ATTEMPTS attempts, reporting failure instead of a false-clean result")
        return null
    }

    companion object {
        private const val TAG = "LintService"

        /** Total generation attempts, matching [IngestService]'s "최대 N회 재생성" phrasing —
         * counts the first try, not just retries after it. */
        private const val MAX_ATTEMPTS = 2

        private const val MIN_HISTORY_FOR_LINT = 2
    }
}
