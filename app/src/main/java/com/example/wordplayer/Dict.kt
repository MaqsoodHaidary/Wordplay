package com.example.wordplayer

import android.content.Context
import android.database.sqlite.SQLiteDatabase

data class Entry(val head: String, val meaning: String)

/** English -> Persian lookup in the bundled fa_en.db (tables e1..e7). */
class Dict(private val ctx: Context) {
    private val tables = listOf("e1", "e2", "e3", "e4", "e5", "e6", "e7")
    private var db: SQLiteDatabase? = null
    @Volatile var ready = false
        private set

    /** Call from a background thread. Copies the DB out of assets once and builds an index. */
    fun open() {
        val f = ctx.getDatabasePath("fa_en.db")
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            val tmp = java.io.File(f.path + ".tmp")
            ctx.assets.open("fa_en.db").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            tmp.renameTo(f)
        }
        val d = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READWRITE)
        for (t in tables) d.execSQL("CREATE INDEX IF NOT EXISTS idx_$t ON $t(en COLLATE NOCASE)")
        db = d
        ready = true
    }

    fun lookup(word: String): List<Entry> {
        val d = db ?: return emptyList()
        for (v in variants(word)) {
            val res = ArrayList<Entry>()
            for (t in tables) {
                d.rawQuery("SELECT en, fa FROM $t WHERE en = ? COLLATE NOCASE LIMIT 5", arrayOf(v)).use { c ->
                    while (c.moveToNext()) res += Entry(c.getString(0), clean(c.getString(1)))
                }
            }
            if (res.isNotEmpty()) {
                // also phrases like "word ( psychology )"
                for (t in tables) {
                    d.rawQuery("SELECT en, fa FROM $t WHERE en LIKE ? LIMIT 3", arrayOf("$v (%")).use { c ->
                        while (c.moveToNext()) res += Entry(c.getString(0), clean(c.getString(1)))
                    }
                }
                return res
            }
        }
        return emptyList()
    }

    private fun variants(w: String): List<String> {
        val v = linkedSetOf(w)
        fun add(s: String) { if (s.length > 1) v.add(s) }
        if (w.endsWith("'s")) add(w.dropLast(2))
        if (w.endsWith("n't")) add(w.dropLast(3))
        if (w.endsWith("'re") || w.endsWith("'ve") || w.endsWith("'ll")) add(w.dropLast(3))
        if (w.endsWith("'m") || w.endsWith("'d")) add(w.dropLast(2))
        if (w.endsWith("ies")) add(w.dropLast(3) + "y")
        if (w.endsWith("ied")) add(w.dropLast(3) + "y")
        if (w.endsWith("es")) add(w.dropLast(2))
        if (w.endsWith("s")) add(w.dropLast(1))
        if (w.endsWith("ed")) {
            add(w.dropLast(2)); add(w.dropLast(1))
            val b = w.dropLast(2)
            if (b.length > 2 && b.last() == b[b.length - 2]) add(b.dropLast(1))
        }
        if (w.endsWith("ing")) {
            val b = w.dropLast(3)
            add(b); add(b + "e")
            if (b.length > 2 && b.last() == b[b.length - 2]) add(b.dropLast(1))
        }
        if (w.endsWith("ly")) add(w.dropLast(2))
        return v.toList()
    }

    /** "<BR>" separated lines; "~field : ~text" -> "▪ field: text"; normalise Arabic letter forms. */
    private fun clean(fa: String): String {
        val fieldLine = Regex("^~\\s*(.*?)\\s*:\\s*~\\s*(.*)$")
        return fa.split(Regex("<BR>", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { line ->
                val m = fieldLine.find(line)
                if (m != null) "▪ ${m.groupValues[1]}: ${m.groupValues[2]}" else line
            }
            .replace('ى', 'ی').replace('ك', 'ک')
    }
}
