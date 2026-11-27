package com.example.wordplayer

data class Cue(val start: Long, val end: Long, val text: String)

/** Parses .srt and .vtt subtitle text. */
object SubtitleParser {
    private val time = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
    private val tags = Regex("<[^>]*>")
    private val braces = Regex("\\{[^}]*\\}")

    fun parse(raw: String): List<Cue> {
        val out = ArrayList<Cue>()
        val blocks = raw.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n{2,}"))
        for (b in blocks) {
            val lines = b.split("\n")
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti < 0) continue
            val ts = time.findAll(lines[ti]).toList()
            if (ts.size < 2) continue
            val text = lines.drop(ti + 1).joinToString("\n")
                .replace(tags, "").replace(braces, "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
                .trim()
            if (text.isEmpty()) continue
            out += Cue(ms(ts[0]), ms(ts[1]), text)
        }
        return out.sortedBy { it.start }
    }

    private fun ms(m: MatchResult): Long {
        val g = m.groupValues
        val h = g[1].ifEmpty { "0" }.toLong()
        val mi = g[2].toLong()
        val s = g[3].toLong()
        val frac = g[4].padEnd(3, '0').take(3).toLong()
        return ((h * 60 + mi) * 60 + s) * 1000 + frac
    }

    /** UTF-8 if valid, otherwise Windows-1252. */
    fun decode(bytes: ByteArray): String {
        var start = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) start = 3
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
        } catch (e: Exception) {
            String(bytes, start, bytes.size - start, charset("windows-1252"))
        }
    }
}
