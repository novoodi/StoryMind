package com.example.storymind.ai

import com.example.storymind.data.WikiEntry
import com.example.storymind.ui.components.SmBadgeType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestServiceTest {

    private val fakeRaw = """
        <|channel>thought
        지우, 민준, 카페, 낯선 남자가 등장한다. 낯선 남자는 아직 아무와도 안 엮였다.
        <channel|>
        {
          "chapter_summary": "지우와 민준이 카페에서 처음 만났다.",
          "entities": [
            {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"},
            {"id":"minjoon","type":"character","name":"박민준","desc":"카페 단골"},
            {"id":"cafe","type":"place","name":"카페 달빛","desc":"만남의 장소"},
            {"id":"stranger","type":"character","name":"수상한 남자","desc":"아직 정체 불명"}
          ],
          "relations": [
            {"from":"jiwoo","to":"minjoon"},
            {"from":"jiwoo","to":"cafe"}
          ]
        }
    """.trimIndent()

    private fun serviceWithFakeEngine() = IngestService(engine = { _, _ -> fakeRaw })

    @Test
    fun `ingest maps entities to wiki entries and placeholder-coordinate nodes`() = runBlocking {
        val result = serviceWithFakeEngine().ingest(
            chapterLabel = "1장",
            title = "빗소리",
            paragraphs = listOf("..."),
        )!!

        assertEquals("지우와 민준이 카페에서 처음 만났다.", result.chapterSummary)
        assertEquals(4, result.wikiEntries.size)
        assertTrue(result.wikiEntries.all { it.chapter == "1장" })
        assertEquals(4, result.nodes.size)
        assertTrue(result.nodes.all { it.x == 0f && it.y == 0f })
        assertEquals(2, result.edges.size)
    }

    @Test
    fun `ingest marks nodes absent from every edge as orphan`() = runBlocking {
        val result = serviceWithFakeEngine().ingest(
            chapterLabel = "1장",
            title = "빗소리",
            paragraphs = listOf("..."),
        )!!

        assertEquals(setOf("stranger"), result.orphanIds)
    }

    @Test
    fun `ingest reuses an existing wiki entry's id when the model mints a new one for the same name`() = runBlocking {
        val rawWithFreshId = """
            {
              "chapter_summary": "율과 진성이 다시 만난다.",
              "entities": [
                {"id":"yul_2","type":"character","name":"율","desc":"업데이트된 설명"},
                {"id":"jinseong_new","type":"character","name":"진성","desc":"새 화의 진성"}
              ],
              "relations": [
                {"from":"yul_2","to":"jinseong_new"}
              ]
            }
        """.trimIndent()
        val service = IngestService(engine = { _, _ -> rawWithFreshId })
        val existingWiki = listOf(
            WikiEntry("yul", SmBadgeType.Character, "율", "1장 설명", "1장"),
            WikiEntry("jinseong", SmBadgeType.Character, "진성", "1장 설명", "1장"),
        )

        val result = service.ingest(
            chapterLabel = "2장",
            title = "2장",
            paragraphs = listOf("..."),
            existingWiki = existingWiki,
        )!!

        assertEquals(setOf("yul", "jinseong"), result.wikiEntries.map { it.id }.toSet())
        assertEquals(1, result.edges.size)
        assertEquals("yul", result.edges[0].from)
        assertEquals("jinseong", result.edges[0].to)
        assertTrue(result.orphanIds.isEmpty())
    }

    @Test
    fun `ingest retries generation when the model's JSON fails to parse, then uses the recovered result`() = runBlocking {
        var callCount = 0
        val service = IngestService(engine = { _, _ ->
            callCount++
            if (callCount < 3) "이건 JSON이 아니라 그냥 잡음입니다" else fakeRaw
        })

        val result = service.ingest(chapterLabel = "1장", title = "빗소리", paragraphs = listOf("..."))!!

        assertEquals(3, callCount)
        assertEquals(4, result.wikiEntries.size)
        assertEquals("지우와 민준이 카페에서 처음 만났다.", result.chapterSummary)
    }

    @Test
    fun `ingest returns null instead of an empty result after exhausting all retries`() = runBlocking {
        // Regression for the 2화 incident (2026-07): a silent empty IngestResult here used to
        // reach IngestWorker indistinguishable from "a chapter with genuinely nothing in it",
        // which committed and flipped ingested=true while the wiki stayed empty. null lets
        // IngestWorker refuse the commit — see IngestService.generateResult's KDoc.
        var callCount = 0
        val service = IngestService(engine = { _, _ ->
            callCount++
            "이건 JSON이 아니라 그냥 잡음입니다"
        })

        val result = service.ingest(chapterLabel = "1장", title = "빗소리", paragraphs = listOf("..."))

        assertEquals(3, callCount)
        assertNull(result)
    }

    @Test
    fun `ingest reuses an existing wiki entry's id when the model uses a surname-dropped nickname`() = runBlocking {
        val rawWithNickname = """
            {
              "chapter_summary": "지민이 다시 등장한다.",
              "entities": [
                {"id":"jimin_2","type":"character","name":"지민","desc":"짧게 불린 이름"}
              ],
              "relations": []
            }
        """.trimIndent()
        val service = IngestService(engine = { _, _ -> rawWithNickname })
        val existingWiki = listOf(
            WikiEntry("jimin", SmBadgeType.Character, "이지민", "1장 설명", "1장"),
        )

        val result = service.ingest(
            chapterLabel = "2장",
            title = "2장",
            paragraphs = listOf("..."),
            existingWiki = existingWiki,
        )!!

        assertEquals(setOf("jimin"), result.wikiEntries.map { it.id }.toSet())
    }

    @Test
    fun `ingest collapses the same entity listed twice in one response into a single wiki entry`() = runBlocking {
        val rawWithDuplicateEntity = """
            {
              "chapter_summary": "지민이 두 번 언급된다.",
              "entities": [
                {"id":"jimin","type":"character","name":"지민","desc":"첫 번째 언급"},
                {"id":"jimin","type":"character","name":"지민","desc":"두 번째 언급"}
              ],
              "relations": []
            }
        """.trimIndent()
        val service = IngestService(engine = { _, _ -> rawWithDuplicateEntity })

        val result = service.ingest(chapterLabel = "1장", title = "1장", paragraphs = listOf("..."))!!

        assertEquals(1, result.wikiEntries.size)
        assertEquals("두 번째 언급", result.wikiEntries[0].desc)
        assertEquals(1, result.nodes.size)
    }

    @Test
    fun `ingest drops relations that reference an entity skipped for unknown type`() = runBlocking {
        val rawWithBadRelation = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"}
              ],
              "relations": [
                {"from":"jiwoo","to":"ghost"}
              ]
            }
        """.trimIndent()
        val service = IngestService(engine = { _, _ -> rawWithBadRelation })

        val result = service.ingest(chapterLabel = "1장", title = "빗소리", paragraphs = listOf("..."))!!

        assertTrue(result.edges.isEmpty())
        assertEquals(setOf("jiwoo"), result.orphanIds)
    }

    @Test
    fun `ingest retries an unresolved relation into a clean attempt instead of accepting the drop early`() = runBlocking {
        // Part 1 of the 2026-07 follow-up batch: an unresolved relation is now a soft failure that
        // shares the parse-failure retry budget, so a mismatch on a non-final attempt should be
        // discarded and retried rather than accepted immediately.
        var callCount = 0
        val mismatchThenClean = { _: String, _: SamplerSettings ->
            callCount++
            if (callCount == 1) {
                """
                    {
                      "chapter_summary": "1차 시도",
                      "entities": [
                        {"id":"율","type":"character","name":"율","desc":"설명"},
                        {"id":"진성","type":"character","name":"진성","desc":"설명"}
                      ],
                      "relations": [
                        {"from":"율","to":"성"}
                      ]
                    }
                """.trimIndent()
            } else {
                """
                    {
                      "chapter_summary": "2차 시도",
                      "entities": [
                        {"id":"율","type":"character","name":"율","desc":"설명"},
                        {"id":"진성","type":"character","name":"진성","desc":"설명"}
                      ],
                      "relations": [
                        {"from":"율","to":"진성"}
                      ]
                    }
                """.trimIndent()
            }
        }
        val service = IngestService(engine = mismatchThenClean)

        val recordedDetails = mutableListOf<String>()
        val previousRecorder = partialDropRecorder
        partialDropRecorder = PartialDropRecorder { detail -> recordedDetails += detail }

        try {
            val result = service.ingest(chapterLabel = "1장", title = "1장", paragraphs = listOf("..."))!!

            assertEquals(2, callCount) // stopped retrying once attempt 2 came back clean
            assertEquals("2차 시도", result.chapterSummary)
            assertEquals(1, result.edges.size)
            assertEquals("율", result.edges[0].from)
            assertEquals("진성", result.edges[0].to)
            assertTrue(recordedDetails.isEmpty()) // the discarded attempt 1 mismatch was never a real drop
        } finally {
            partialDropRecorder = previousRecorder
        }
    }

    @Test
    fun `ingest accepts the last attempt's unresolved relation after exhausting retries, recording the drop only once`() = runBlocking {
        var callCount = 0
        val alwaysMismatched = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"율","type":"character","name":"율","desc":"설명"},
                {"id":"진성","type":"character","name":"진성","desc":"설명"},
                {"id":"엄마","type":"character","name":"엄마","desc":"설명"}
              ],
              "relations": [
                {"from":"율","to":"진성"},
                {"from":"성","to":"엄마"}
              ]
            }
        """.trimIndent()
        val service = IngestService(engine = { _, _ ->
            callCount++
            alwaysMismatched
        })

        val recordedDetails = mutableListOf<String>()
        val previousRecorder = partialDropRecorder
        partialDropRecorder = PartialDropRecorder { detail -> recordedDetails += detail }

        try {
            val result = service.ingest(chapterLabel = "1장", title = "1장", paragraphs = listOf("..."))!!

            assertEquals(3, callCount) // no extra attempts added for the soft failure
            // The one valid relation still merges...
            assertEquals(1, result.edges.size)
            assertEquals("율", result.edges[0].from)
            assertEquals("진성", result.edges[0].to)
            // ...엄마 exists but ends up orphaned, not silently missing.
            assertEquals(setOf("엄마"), result.orphanIds)
            // ...and the drop is recorded exactly once — attempts 1 and 2's identical mismatch
            // were discarded as soft failures, not recorded; only attempt 3 (the accepted one) is.
            assertEquals(1, recordedDetails.size)
        } finally {
            partialDropRecorder = previousRecorder
        }
    }

    @Test
    fun `ingest shares its attempt budget between parse failures and unresolved-relation soft failures`() = runBlocking {
        var callCount = 0
        val service = IngestService(engine = { _, _ ->
            callCount++
            when (callCount) {
                1 -> "이건 JSON이 아니라 그냥 잡음입니다"
                else -> """
                    {
                      "chapter_summary": "요약",
                      "entities": [
                        {"id":"율","type":"character","name":"율","desc":"설명"},
                        {"id":"진성","type":"character","name":"진성","desc":"설명"}
                      ],
                      "relations": [
                        {"from":"율","to":"성"}
                      ]
                    }
                """.trimIndent()
            }
        })

        val recordedDetails = mutableListOf<String>()
        val previousRecorder = partialDropRecorder
        partialDropRecorder = PartialDropRecorder { detail -> recordedDetails += detail }

        try {
            val result = service.ingest(chapterLabel = "1장", title = "1장", paragraphs = listOf("..."))!!

            // attempt 1: parse failure. attempt 2: mismatch (not last, discarded). attempt 3:
            // mismatch (last, accepted). Three failure-flavored attempts, still just MAX_ATTEMPTS.
            assertEquals(3, callCount)
            assertTrue(result.edges.isEmpty())
            assertEquals(1, recordedDetails.size)
        } finally {
            partialDropRecorder = previousRecorder
        }
    }

    @Test
    fun `ingest logs and records a relation whose id never appears in this response's entities`() = runBlocking {
        // Regression for the 2026-07 1화 incident: Gemma defined "진성" as an entity but wrote one
        // relation against the truncated id "성", which matches nothing in this response's own
        // entities[] — silently dropped before, now surfaced via ingestLogger + partialDropRecorder.
        val rawWithTruncatedId = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"율","type":"character","name":"율","desc":"설명"},
                {"id":"진성","type":"character","name":"진성","desc":"설명"},
                {"id":"엄마","type":"character","name":"엄마","desc":"설명"}
              ],
              "relations": [
                {"from":"율","to":"진성"},
                {"from":"성","to":"엄마"}
              ]
            }
        """.trimIndent()
        val service = IngestService(engine = { _, _ -> rawWithTruncatedId })

        val loggedWarnings = mutableListOf<String>()
        val recordedDetails = mutableListOf<String>()
        val previousLogger = ingestLogger
        val previousRecorder = partialDropRecorder
        ingestLogger = object : IngestLogger {
            override fun d(tag: String, message: String) = Unit
            override fun w(tag: String, message: String) {
                loggedWarnings += message
            }
            override fun w(tag: String, message: String, throwable: Throwable) {
                loggedWarnings += message
            }
        }
        partialDropRecorder = PartialDropRecorder { detail -> recordedDetails += detail }

        try {
            val result = service.ingest(chapterLabel = "1장", title = "1장", paragraphs = listOf("..."))!!

            // The valid relation still merges, and the whole ingest is still a success...
            assertEquals(1, result.edges.size)
            assertEquals("율", result.edges[0].from)
            assertEquals("진성", result.edges[0].to)
            // ...엄마 exists but ends up orphaned, not silently missing from the wiki entirely.
            assertEquals(setOf("엄마"), result.orphanIds)

            // ...but the drop itself is surfaced instead of vanishing without a trace.
            assertTrue(loggedWarnings.any { it.contains("성") && it.contains("엄마") })
            assertEquals(1, recordedDetails.size)
            assertTrue(recordedDetails[0].contains("성 -> 엄마"))
            assertTrue(recordedDetails[0].contains("진성"))
        } finally {
            ingestLogger = previousLogger
            partialDropRecorder = previousRecorder
        }
    }

    @Test
    fun `ingest escalates sampler temperature across retries instead of repeating attempt 1's settings`() = runBlocking {
        val seenTemperatures = mutableListOf<Double>()
        val seenSeeds = mutableListOf<Int>()
        val service = IngestService(engine = { _, sampler ->
            seenTemperatures += sampler.temperature
            seenSeeds += sampler.seed
            if (seenTemperatures.size < 3) "이건 JSON이 아니라 그냥 잡음입니다" else fakeRaw
        })

        service.ingest(chapterLabel = "1장", title = "빗소리", paragraphs = listOf("..."))

        assertEquals(3, seenTemperatures.size)
        // Strictly increasing: each retry widens sampling instead of reusing attempt 1's
        // near-deterministic settings — see IngestService.samplerForAttempt's KDoc.
        assertTrue(seenTemperatures[0] < seenTemperatures[1])
        assertTrue(seenTemperatures[1] < seenTemperatures[2])
        // Distinct seeds so a retried chapter doesn't necessarily replay the same three
        // generations that just failed.
        assertEquals(3, seenSeeds.toSet().size)
    }

    @Test
    fun `shadowMatchDescription finds a suffix candidate`() {
        // The 2026-07 1화 incident's exact shape: "성" is "진성" with the leading syllable dropped.
        val description = IngestService.shadowMatchDescription("성", setOf("율", "진성", "지민"))

        assertEquals("shadow_match: 성 -> 진성 (suffix)", description)
    }

    @Test
    fun `shadowMatchDescription finds a prefix candidate`() {
        // The "엄마음" incident's shape: "엄마" plus an extra trailing syllable.
        val description = IngestService.shadowMatchDescription("엄마음", setOf("율", "진성", "엄마"))

        assertEquals("shadow_match: 엄마음 -> 엄마 (prefix)", description)
    }

    @Test
    fun `shadowMatchDescription reports ambiguous when two candidates qualify`() {
        val description = IngestService.shadowMatchDescription("성", setOf("진성", "완성"))

        assertTrue(description.startsWith("ambiguous: 2 candidates"))
        assertTrue(description.contains("진성"))
        assertTrue(description.contains("완성"))
    }

    @Test
    fun `shadowMatchDescription reports no candidate when nothing qualifies`() {
        val description = IngestService.shadowMatchDescription("성", setOf("율", "지민"))

        assertEquals("no candidate", description)
    }

    @Test
    fun `shadowMatchDescription treats a blank id as no candidate instead of matching everything`() {
        // A blank unresolvedId would make String.contains("") trivially true for every knownId —
        // guarded explicitly since this is a degenerate-input case, not a length-floor one (see
        // shadowMatchDescription's KDoc for why there's no length floor on unresolvedId itself).
        val description = IngestService.shadowMatchDescription("", setOf("율", "진성"))

        assertEquals("no candidate (blank id)", description)
    }

    @Test
    fun `ingest includes a shadow-match line in the recorded drop detail without changing the merge`() = runBlocking {
        // Part 2 of the 2026-07 follow-up batch: shadow mode only annotates the existing drop
        // record — it must never resolve the relation into the merged graph.
        val rawWithTruncatedId = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"율","type":"character","name":"율","desc":"설명"},
                {"id":"진성","type":"character","name":"진성","desc":"설명"},
                {"id":"엄마","type":"character","name":"엄마","desc":"설명"}
              ],
              "relations": [
                {"from":"율","to":"진성"},
                {"from":"성","to":"엄마"}
              ]
            }
        """.trimIndent()
        val service = IngestService(engine = { _, _ -> rawWithTruncatedId })

        val recordedDetails = mutableListOf<String>()
        val previousRecorder = partialDropRecorder
        partialDropRecorder = PartialDropRecorder { detail -> recordedDetails += detail }

        try {
            val result = service.ingest(chapterLabel = "1장", title = "1장", paragraphs = listOf("..."))!!

            // Merge behavior unchanged: still just the one clean relation, "엄마" still orphaned.
            assertEquals(1, result.edges.size)
            assertEquals(setOf("엄마"), result.orphanIds)

            assertEquals(1, recordedDetails.size)
            assertTrue(recordedDetails[0].contains("shadow_match: 성 -> 진성 (suffix)"))
        } finally {
            partialDropRecorder = previousRecorder
        }
    }
}