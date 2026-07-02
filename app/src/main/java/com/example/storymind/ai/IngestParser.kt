package com.example.storymind.ai

import com.example.storymind.ui.components.SmBadgeType
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

data class ParsedEntity(
    val id: String,
    val type: SmBadgeType,
    val name: String,
    val desc: String,
)

data class ParsedRelation(val from: String, val to: String)

data class ParsedIngest(
    val chapterSummary: String,
    val entities: List<ParsedEntity>,
    val relations: List<ParsedRelation>,
)

/**
 * Turns the raw string returned by the on-device engine into structured ingest data:
 * strips the Gemma thinking-mode `<|channel>thought ... <channel|>` block, extracts the
 * JSON object that follows, repairs the trailing/missing commas, dropped "id" keys, and stray
 * duplicate closing braces Gemma tends to emit, then parses it. Any failure falls back to an
 * empty result instead of throwing.
 */
object IngestParser {

    private const val TAG = "IngestParser"

    private val THOUGHT_BLOCK_REGEX = Regex("<\\|channel>thought.*?<channel\\|>", RegexOption.DOT_MATCHES_ALL)
    private val TRAILING_COMMA_REGEX = Regex(",\\s*([}\\]])")

    /**
     * Matches the gap between one JSON value/token ending and the next value/object/array
     * starting with no comma in between, e.g. `"꿈 기록부"\n      "type"` — a comma omission
     * Gemma occasionally makes between sibling object members or array elements.
     */
    private val MISSING_COMMA_REGEX = Regex("(\"|\\}|\\]|true|false|null|\\d)(\\s+)(?=\"|\\{|\\[)")

    /**
     * Matches a bare `": ` sitting where a key name belongs — right after `{` or `,` — which
     * Gemma occasionally emits by dropping just the key name and quotes but keeping the colon.
     * It shows up in two shapes: `{\n  ": "구름 의자",` (the key was the object's first/only
     * missing one — always "id" per [IngestSchema]) and `,\n  ": "desc": "...",` (a phantom
     * prefix immediately before an otherwise-intact real key, which just needs deleting).
     * [repairBareKeys] tells the two apart by whether a real `"key":` follows.
     */
    private val BARE_KEY_REGEX = Regex("([{,]\\s*)\":\\s*")
    private val REAL_KEY_LOOKAHEAD_REGEX = Regex("^\"[^\"]*\"\\s*:")

    /**
     * Matches a doubled `}}` right before a `,` or `]` — Gemma sometimes tacks on one extra
     * closing brace when it writes an entity/relation object inline on a single line, e.g.
     * `"desc": "..." }},`. [IngestSchema]'s entities/relations objects never nest, so two
     * closing braces in a row can only be this stray duplicate; collapsing it to one is safe.
     */
    private val DOUBLE_CLOSE_BRACE_REGEX = Regex("\\}\\s*\\}(?=\\s*[,\\]])")

    /**
     * Matches a doubled `{{` — a decoding stutter where Gemma repeats the object-open token
     * before an entity/relation, e.g. `{\n  {\n  "id": "김진성", ...`. Left alone, the inner `{`
     * is read as an attempted object key where a string is expected, which no JSON parser accepts.
     * Since these objects never nest, collapsing to a single `{` is always safe.
     * Must run before [DOUBLE_CLOSE_BRACE_REGEX]: Gemma sometimes emits a matching doubled `}}`
     * for the same stray open (`{ { .. } }`), which only becomes a lone trailing double-close
     * once this pass removes the extra open.
     */
    private val DOUBLE_OPEN_BRACE_REGEX = Regex("\\{\\s*\\{")

    /**
     * Matches a stray `",` fragment sandwiched between two real commas — a lone quote with
     * nothing before or after it inside the pair — which Gemma occasionally emits right after a
     * complete `"key": "value",` member, e.g. `"id": "엘라시움",      ",\n  "type": ...`. Two
     * commas separated only by whitespace and a single quote can never be legitimate JSON (a
     * real second comma always follows a complete value), so collapsing the pair down to one
     * comma is always safe.
     */
    private val STRAY_QUOTE_COMMA_REGEX = Regex(",\\s*\"\\s*,")

    private val EMPTY = ParsedIngest(chapterSummary = "", entities = emptyList(), relations = emptyList())

    /**
     * `isLenient` tolerates loosely-typed/unquoted primitives Gemma occasionally emits;
     * `ignoreUnknownKeys` (exercised when decoding individual entity/relation objects — see
     * [decodeEntityOrNull]/[decodeRelationOrNull]) tolerates extra fields beyond [IngestSchema]'s
     * contract. Neither substitutes for the regex repairs above: those fix outright grammar
     * violations (missing commas/keys, duplicated braces) that no JSON leniency mode treats as
     * valid input, so none of them became removable when this parser moved off org.json.
     */
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun parse(raw: String): ParsedIngest = parseOrNull(raw) ?: EMPTY

    /**
     * Same as [parse] but returns null instead of falling back to [EMPTY] when the model's
     * output couldn't be turned into JSON. This lets [IngestService] tell "nothing usable to
     * parse" apart from "a real empty chapter" and retry the generation instead of silently
     * losing the chapter's wiki data to one of Gemma's many one-off JSON slip-ups.
     */
    fun parseOrNull(raw: String): ParsedIngest? {
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
                    collapseDoubleCloseBraces(
                        collapseDoubleOpenBraces(collapseStrayQuoteCommas(repairBareKeys(jsonText)))
                    )
                )
            )
            ingestLogger.d(TAG, "parse() repaired JSON: $repaired")
            val root = json.parseToJsonElement(repaired) as? JsonObject
                ?: throw SerializationException("Top-level JSON element is not an object: $repaired")
            root.toParsedIngest()
        } catch (e: SerializationException) {
            ingestLogger.w(TAG, "Failed to parse ingest JSON", e)
            null
        }
    }

    private fun extractJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }

    private fun insertMissingCommas(json: String): String =
        json.replace(MISSING_COMMA_REGEX, "$1,$2")

    private fun repairBareKeys(json: String): String =
        BARE_KEY_REGEX.replace(json) { match ->
            val prefix = match.groupValues[1]
            val after = json.substring(match.range.last + 1)
            if (REAL_KEY_LOOKAHEAD_REGEX.containsMatchIn(after)) prefix else "$prefix\"id\": "
        }

    private fun collapseDoubleCloseBraces(json: String): String =
        json.replace(DOUBLE_CLOSE_BRACE_REGEX, "}")

    private fun collapseStrayQuoteCommas(json: String): String =
        json.replace(STRAY_QUOTE_COMMA_REGEX, ",")

    private fun collapseDoubleOpenBraces(json: String): String =
        json.replace(DOUBLE_OPEN_BRACE_REGEX, "{")

    private fun stripTrailingCommas(json: String): String {
        var previous: String
        var current = json
        do {
            previous = current
            current = TRAILING_COMMA_REGEX.replace(current, "$1")
        } while (current != previous)
        return current
    }

    /** Wire shape for one entities[]/relations[] element. All fields nullable-with-null-default so a
     * missing key and an explicit JSON `null` behave identically — matching org.json's optString,
     * which historically backed this parser and treated both the same way. */
    @Serializable
    private data class GemmaEntity(
        val id: String? = null,
        val type: String? = null,
        val name: String? = null,
        val desc: String? = null,
    )

    @Serializable
    private data class GemmaRelation(
        val from: String? = null,
        val to: String? = null,
    )

    private fun JsonObject.toParsedIngest(): ParsedIngest {
        val summary = optString("chapter_summary") ?: ""

        val entities = mutableListOf<ParsedEntity>()
        (this["entities"] as? JsonArray)?.forEach { element ->
            val entityObject = element as? JsonObject ?: return@forEach
            val entity = decodeEntityOrNull(entityObject) ?: return@forEach
            val id = entity.id.orEmpty().takeIf { it.isNotBlank() } ?: return@forEach
            val rawType = entity.type.orEmpty()
            val type = mapType(rawType)
            if (type == null) {
                ingestLogger.w(TAG, "Skipping entity '$id' with unknown type '$rawType'")
                return@forEach
            }
            entities += ParsedEntity(
                id = id,
                type = type,
                name = entity.name ?: id,
                desc = entity.desc.orEmpty(),
            )
        }

        val relations = mutableListOf<ParsedRelation>()
        (this["relations"] as? JsonArray)?.forEach { element ->
            val relationObject = element as? JsonObject ?: return@forEach
            val relation = decodeRelationOrNull(relationObject) ?: return@forEach
            val from = relation.from.orEmpty().takeIf { it.isNotBlank() } ?: return@forEach
            val to = relation.to.orEmpty().takeIf { it.isNotBlank() } ?: return@forEach
            relations += ParsedRelation(from, to)
        }

        return ParsedIngest(summary, entities, relations)
    }

    /** Non-object array elements are skipped by the `as? JsonObject` cast above before this runs;
     * this catches the narrower case of an object that doesn't decode into [GemmaEntity] at all
     * (mirrors org.json's `optJSONObject(i) ?: continue` never throwing on a bad element). */
    private fun decodeEntityOrNull(entityObject: JsonObject): GemmaEntity? = try {
        json.decodeFromJsonElement<GemmaEntity>(entityObject)
    } catch (e: SerializationException) {
        ingestLogger.w(TAG, "Skipping malformed entity object: $entityObject", e)
        null
    }

    private fun decodeRelationOrNull(relationObject: JsonObject): GemmaRelation? = try {
        json.decodeFromJsonElement<GemmaRelation>(relationObject)
    } catch (e: SerializationException) {
        ingestLogger.w(TAG, "Skipping malformed relation object: $relationObject", e)
        null
    }

    private fun JsonObject.optString(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun mapType(raw: String): SmBadgeType? = when (raw.trim().lowercase()) {
        "character" -> SmBadgeType.Character
        "place" -> SmBadgeType.Place
        "item" -> SmBadgeType.Item
        "event" -> SmBadgeType.Event
        else -> null
    }
}
