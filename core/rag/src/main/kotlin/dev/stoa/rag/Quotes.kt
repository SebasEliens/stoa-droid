package dev.stoa.rag

/** Shortest excerpt we accept as a quote; anything shorter can match by accident. */
const val MIN_QUOTE_CHARS = 20

/**
 * Finds [excerpt] in [passage], ignoring case, whitespace, and curly-vs-straight quotes and dashes.
 * Returns the matching slice of [passage] itself, so displayed quotes are always the source text.
 */
fun findExcerpt(
    passage: String,
    excerpt: String,
): String? {
    val (normPassage, index) = normalize(passage)
    val normExcerpt = normalize(excerpt.trim().trim('"', '“', '”', '\'', '‘', '’', '…', '.', ' ')).first
    val start = if (normExcerpt.length < MIN_QUOTE_CHARS) -1 else normPassage.indexOf(normExcerpt)
    return if (start < 0) null else passage.substring(index[start], index[start + normExcerpt.length - 1] + 1)
}

/** Normalized text plus, for each normalized char, its index in the original. */
private fun normalize(s: String): Pair<String, IntArray> {
    val out = StringBuilder()
    val index = ArrayList<Int>(s.length)
    for ((i, c) in s.withIndex()) {
        val n =
            when (c) {
                '‘', '’', '`' -> '\''
                '“', '”' -> '"'
                '–', '—' -> '-'
                else -> if (c.isWhitespace()) ' ' else c.lowercaseChar()
            }
        if (n == ' ' && (out.isEmpty() || out.last() == ' ')) continue
        out.append(n)
        index.add(i)
    }
    return out.toString() to index.toIntArray()
}
