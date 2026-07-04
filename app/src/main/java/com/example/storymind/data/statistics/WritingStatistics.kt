package com.example.storymind.data.statistics

import com.example.storymind.data.db.ChapterEntity

/** One chapter's contribution to the writing stats. [chars] mirrors the editor's "N자" counter
 * ([ChapterEntity.body] length, whitespace included) so the number a writer sees while typing and
 * the number here agree. [chapterIndex] is the 0-based key; the "N화" label is index + 1. */
data class ChapterStat(val chapterIndex: Int, val chars: Int)

/** Aggregate writing statistics derived purely from the chapters. Kept free of file/DB I/O for the
 * same reason as [com.example.storymind.data.backup.formatManuscriptTxt] — so a JVM unit test can
 * pin the numbers. Time-based trends aren't here: chapters carry no timestamp, so [perChapter] is
 * ordered by chapterIndex (the only axis the schema supports without a migration). */
data class WritingStats(
    val totalChars: Int = 0,
    /** Chapters with a non-blank body — a saved-but-empty draft isn't manuscript (same rule the
     * exporters and the analyze-resume use). */
    val writtenChapters: Int = 0,
    val analyzedChapters: Int = 0,
    val avgChars: Int = 0,
    val longest: ChapterStat? = null,
    val perChapter: List<ChapterStat> = emptyList(),
)

/**
 * Computes [WritingStats] from the full chapter list. Blank-body chapters are excluded everywhere
 * (they contribute no manuscript), so counts and averages reflect only real writing. The average is
 * integer division — good enough for a headline number and keeps the output deterministic for tests.
 */
fun computeWritingStats(chapters: List<ChapterEntity>): WritingStats {
    val written = chapters.filter { it.body.isNotBlank() }
    val perChapter = written
        .map { ChapterStat(it.chapterIndex, it.body.length) }
        .sortedBy { it.chapterIndex }
    val total = perChapter.sumOf { it.chars }
    val count = perChapter.size
    return WritingStats(
        totalChars = total,
        writtenChapters = count,
        analyzedChapters = written.count { it.ingested },
        avgChars = if (count == 0) 0 else total / count,
        longest = perChapter.maxByOrNull { it.chars },
        perChapter = perChapter,
    )
}
