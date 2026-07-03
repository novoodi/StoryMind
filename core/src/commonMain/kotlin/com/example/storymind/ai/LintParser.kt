package com.example.storymind.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

data class LintFinding(
    val entityId: String,
    val verdict: LintVerdict,
    val chapterEvidence: String,
    val wikiEvidence: String,
    val reason: String,
)

enum class LintVerdict { Conflict, Development, Ambiguous }

/**
 * Turns the raw string returned by the on-device engine into a list of [LintFinding]s.
 *
 * This is a sibling of [IngestParser], not a copy of it: [LintSchema] prepends `<|think|>` to
 * every prompt to attempt to turn thinking mode on, so stripping a leading thought block is
 * attempted unconditionally here rather than treated as a repair for an occasional slip the way
 * ingest treats it. In practice `docs/lint-golden-baseline.md`'s v3 measurement never found a
 * `<|channel>thought ... <channel|>` block to strip (0/15 runs) — it's unclear whether
 * [LintSchema.THINK_TOKEN] actually enables thinking mode for this model/LiteRT-LM combination.
 * The strip stays in as a defensive no-op rather than being removed: it's cheap, and correct if
 * that ever changes (a different prompt, a different model revision).
 *
 * The rest of [IngestParser]'s repair regexes were re-examined against the lint schema's actual
 * shape rather than ported wholesale (CLAUDE.md's parser-schema coupling note — each repair
 * regex encodes an assumption about a specific schema's structure):
 * - Kept: trailing commas, a missing comma between sibling members, doubled `{{`/`}}` around a
 *   finding object, and a stray quote-comma fragment after a member. All four rely only on
 *   generic JSON grammar or "finding objects don't nest," which holds here the same way
 *   "entity/relation objects don't nest" holds for [IngestSchema] — the assumption transfers even
 *   though the schema doesn't; none of them reference a specific key name.
 * - Dropped: [IngestParser]'s bare-first-key repair. It hardcodes the recovered key name as
 *   `"id"`, which is specific to entities always leading with an `id` field; a finding's first
 *   field is `entity_id`, so porting the same regex would silently insert the wrong key. There is
 *   also no on-device evidence yet that Gemma drops a lint finding's first key the way it drops
 *   an entity's — worth adding if that shows up in practice, not before.
 */
object LintParser {

    private const val TAG = "LintParser"

    private val THOUGHT_BLOCK_REGEX = Regex("<\\|channel>thought.*?<channel\\|>", RegexOption.DOT_MATCHES_ALL)
    private val TRAILING_COMMA_REGEX = Regex(",\\s*([}\\]])")
    private val MISSING_COMMA_REGEX = Regex("(\"|\\}|\\]|true|false|null|\\d)(\\s+)(?=\"|\\{|\\[)")
    private val DOUBLE_CLOSE_BRACE_REGEX = Regex("\\}\\s*\\}(?=\\s*[,\\]])")
    private val DOUBLE_OPEN_BRACE_REGEX = Regex("\\{\\s*\\{")
    private val STRAY_QUOTE_COMMA_REGEX = Regex(",\\s*\"\\s*,")

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun parse(raw: String): List<LintFinding> = parseOrNull(raw) ?: emptyList()

    /**
     * Same as [parse] but returns null instead of an empty list when the model's output couldn't
     * be turned into JSON at all — lets [LintService] tell "nothing usable to parse" apart from
     * "checked, no findings" and retry the generation instead of quietly reporting a clean bill
     * of health that was really a parse failure.
     */
    fun parseOrNull(raw: String): List<LintFinding>? {
        THOUGHT_BLOCK_REGEX.find(raw)?.let {
            ingestLogger.d(TAG, "Stripped thought block: ${it.value.length} chars (raw total ${raw.length} chars)")
        }
        val withoutThought = raw.replace(THOUGHT_BLOCK_REGEX, "")

        val jsonText = extractJsonBlock(withoutThought)
        if (jsonText == null) {
            ingestLogger.w(TAG, "No JSON object found in model output: $raw")
            return null
        }

        return try {
            val repaired = stripTrailingCommas(
                insertMissingCommas(
                    collapseDoubleCloseBraces(collapseDoubleOpenBraces(collapseStrayQuoteCommas(jsonText)))
                )
            )
            ingestLogger.d(TAG, "parse() repaired JSON: $repaired")
            val root = json.parseToJsonElement(repaired) as? JsonObject
                ?: throw SerializationException("Top-level JSON element is not an object: $repaired")
            root.toFindings()
        } catch (e: SerializationException) {
            ingestLogger.w(TAG, "Failed to parse lint JSON", e)
            null
        }
    }

    /** Extracts the substring between the first `{` and the last `}`, which discards a
     * ```` ```json ```` code fence (and any other prose around the object) the same way
     * [IngestParser]'s identical extraction does. */
    private fun extractJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }

    private fun insertMissingCommas(json: String): String =
        json.replace(MISSING_COMMA_REGEX, "$1,$2")

    private fun collapseDoubleCloseBraces(json: String): String =
        json.replace(DOUBLE_CLOSE_BRACE_REGEX, "}")

    private fun collapseDoubleOpenBraces(json: String): String =
        json.replace(DOUBLE_OPEN_BRACE_REGEX, "{")

    private fun collapseStrayQuoteCommas(json: String): String =
        json.replace(STRAY_QUOTE_COMMA_REGEX, ",")

    private fun stripTrailingCommas(json: String): String {
        var previous: String
        var current = json
        do {
            previous = current
            current = TRAILING_COMMA_REGEX.replace(current, "$1")
        } while (current != previous)
        return current
    }

    @Serializable
    private data class GemmaFinding(
        @SerialName("entity_id") val entityId: String? = null,
        val verdict: String? = null,
        @SerialName("chapter_evidence") val chapterEvidence: String? = null,
        @SerialName("wiki_evidence") val wikiEvidence: String? = null,
        val reason: String? = null,
    )

    private fun JsonObject.toFindings(): List<LintFinding> {
        val findings = mutableListOf<LintFinding>()
        (this["findings"] as? JsonArray)?.forEach { element ->
            val findingObject = element as? JsonObject ?: return@forEach
            val finding = decodeFindingOrNull(findingObject) ?: return@forEach
            val entityId = finding.entityId.orEmpty().takeIf { it.isNotBlank() } ?: return@forEach
            val rawVerdict = finding.verdict.orEmpty()
            val verdict = mapVerdict(rawVerdict)
            if (verdict == null) {
                ingestLogger.w(TAG, "Skipping finding for '$entityId' with unknown verdict '$rawVerdict'")
                return@forEach
            }
            findings += LintFinding(
                entityId = entityId,
                verdict = verdict,
                chapterEvidence = finding.chapterEvidence.orEmpty(),
                wikiEvidence = finding.wikiEvidence.orEmpty(),
                reason = finding.reason.orEmpty(),
            )
        }
        return findings
    }

    private fun decodeFindingOrNull(findingObject: JsonObject): GemmaFinding? = try {
        json.decodeFromJsonElement<GemmaFinding>(findingObject)
    } catch (e: SerializationException) {
        ingestLogger.w(TAG, "Skipping malformed finding object: $findingObject", e)
        null
    }

    private fun mapVerdict(raw: String): LintVerdict? = when (raw.trim().lowercase()) {
        "conflict" -> LintVerdict.Conflict
        "development" -> LintVerdict.Development
        "ambiguous" -> LintVerdict.Ambiguous
        else -> null
    }
}
