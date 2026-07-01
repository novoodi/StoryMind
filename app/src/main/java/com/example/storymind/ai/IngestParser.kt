package com.example.storymind.ai

import android.util.Log
import com.example.storymind.ui.components.SmBadgeType
import org.json.JSONException
import org.json.JSONObject

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
 * JSON object that follows, repairs the trailing commas Gemma tends to emit, then parses it.
 * Any failure falls back to an empty result instead of throwing.
 */
object IngestParser {

    private const val TAG = "IngestParser"

    private val THOUGHT_BLOCK_REGEX = Regex("<\\|channel>thought.*?<channel\\|>", RegexOption.DOT_MATCHES_ALL)
    private val TRAILING_COMMA_REGEX = Regex(",\\s*([}\\]])")
    private val EMPTY = ParsedIngest(chapterSummary = "", entities = emptyList(), relations = emptyList())

    fun parse(raw: String): ParsedIngest {
        THOUGHT_BLOCK_REGEX.find(raw)?.let { Log.d(TAG, "Stripped thought block: ${it.value}") }
        val withoutThought = raw.replace(THOUGHT_BLOCK_REGEX, "")

        val jsonText = extractJsonBlock(withoutThought)
        if (jsonText == null) {
            Log.w(TAG, "No JSON object found in model output: $raw")
            return EMPTY
        }

        return try {
            JSONObject(stripTrailingCommas(jsonText)).toParsedIngest()
        } catch (e: JSONException) {
            Log.w(TAG, "Failed to parse ingest JSON, falling back to empty result", e)
            EMPTY
        }
    }

    private fun extractJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }

    private fun stripTrailingCommas(json: String): String {
        var previous: String
        var current = json
        do {
            previous = current
            current = TRAILING_COMMA_REGEX.replace(current, "$1")
        } while (current != previous)
        return current
    }

    private fun JSONObject.toParsedIngest(): ParsedIngest {
        val summary = optString("chapter_summary", "")

        val entities = mutableListOf<ParsedEntity>()
        optJSONArray("entities")?.let { array ->
            for (i in 0 until array.length()) {
                val entity = array.optJSONObject(i) ?: continue
                val id = entity.optString("id").takeIf { it.isNotBlank() } ?: continue
                val rawType = entity.optString("type")
                val type = mapType(rawType)
                if (type == null) {
                    Log.w(TAG, "Skipping entity '$id' with unknown type '$rawType'")
                    continue
                }
                entities += ParsedEntity(
                    id = id,
                    type = type,
                    name = entity.optString("name", id),
                    desc = entity.optString("desc", ""),
                )
            }
        }

        val relations = mutableListOf<ParsedRelation>()
        optJSONArray("relations")?.let { array ->
            for (i in 0 until array.length()) {
                val relation = array.optJSONObject(i) ?: continue
                val from = relation.optString("from").takeIf { it.isNotBlank() } ?: continue
                val to = relation.optString("to").takeIf { it.isNotBlank() } ?: continue
                relations += ParsedRelation(from, to)
            }
        }

        return ParsedIngest(summary, entities, relations)
    }

    private fun mapType(raw: String): SmBadgeType? = when (raw.trim().lowercase()) {
        "character" -> SmBadgeType.Character
        "place" -> SmBadgeType.Place
        "item" -> SmBadgeType.Item
        "event" -> SmBadgeType.Event
        else -> null
    }
}