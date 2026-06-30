package com.s3id3l.voicecapture.data

/**
 * Pure text-assembly helpers for combining recording sessions.
 *
 * Used by both the merge flow (combine N independent recordings into one) and the
 * resume flow (append a new segment to an existing recording). Kept free of Android
 * dependencies so the combining logic can be unit-tested directly.
 */
object SessionMerge {

    const val SEGMENT_SEPARATOR = "\n\n─────────\n\n"

    /** Joins segment transcripts in the given order; blanks are skipped. */
    fun buildMergedTranscript(transcripts: List<String>): String =
        transcripts.map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(SEGMENT_SEPARATOR)

    /** Joins segment outputs in the given order; blanks are skipped. */
    fun buildMergedOutput(outputs: List<String>): String =
        outputs.map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString("\n\n")

    /** Title for a merged session: first up to 3 non-empty titles, " + "-joined, capped at 80 chars. */
    fun buildMergedTitle(titles: List<String>): String =
        titles.map { it.trim() }.filter { it.isNotEmpty() }
            .take(3).joinToString(" + ").take(80)
            .ifEmpty { "Zusammengeführt" }

    /**
     * Appends a new segment to existing content. The original is always preserved and the
     * new segment is added after a separator — existing content is never overwritten.
     */
    fun appendSegment(existing: String, segment: String): String {
        val a = existing.trim()
        val b = segment.trim()
        return when {
            a.isEmpty() -> b
            b.isEmpty() -> a
            else        -> a + SEGMENT_SEPARATOR + b
        }
    }
}
