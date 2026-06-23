package com.s3id3l.voicecapture.util

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/**
 * Lightweight markdown-to-Spannable formatter for display TextViews.
 * Intentionally minimal — no external library.
 *
 * Supports:
 *  - **bold** / *bold*  → bold span
 *  - [ ] / [x]          → ☐ / ☑
 *  - lines "- " / "* "  → "•  " bullet
 *  - lines "# " / "## " → bold + slightly larger
 */
object MarkdownFormatter {

    private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*|\*(.+?)\*""")
    private val HEADING_REGEX = Regex("""^(#{1,3})\s+(.*)""")
    private val BULLET_REGEX = Regex("""^[-*]\s+""")

    fun format(raw: String): CharSequence {
        val sb = SpannableStringBuilder()
        val lines = raw.lines()
        lines.forEachIndexed { idx, lineRaw ->
            var line = lineRaw
                .replace(Regex("""\[\s\]"""), "☐")
                .replace(Regex("""\[[xX]\]"""), "☑")

            var isHeading = false
            HEADING_REGEX.find(line)?.let { m ->
                isHeading = true
                line = m.groupValues[2]
            }

            line = BULLET_REGEX.replace(line, "•  ")

            val lineStart = sb.length
            appendWithBold(sb, line)

            if (isHeading) {
                sb.setSpan(StyleSpan(Typeface.BOLD), lineStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(1.15f), lineStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (idx < lines.size - 1) sb.append("\n")
        }
        return sb
    }

    private fun appendWithBold(sb: SpannableStringBuilder, line: String) {
        var last = 0
        for (m in BOLD_REGEX.findAll(line)) {
            if (m.range.first > last) sb.append(line.substring(last, m.range.first))
            val content = m.groupValues[1].ifEmpty { m.groupValues[2] }
            val start = sb.length
            sb.append(content)
            sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            last = m.range.last + 1
        }
        if (last < line.length) sb.append(line.substring(last))
    }
}
