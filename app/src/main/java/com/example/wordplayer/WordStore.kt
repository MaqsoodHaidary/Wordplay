package com.example.wordplayer

import android.content.Context

/** Known words and review-later words, saved on the device. */
class WordStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("words", Context.MODE_PRIVATE)
    private val known = HashSet(prefs.getStringSet("known", emptySet()) ?: emptySet())
    private val review = LinkedHashSet<String>(
        (prefs.getString("review", "") ?: "").split("\n").filter { it.isNotEmpty() }
    )

    fun isKnown(w: String) = w in known
    fun isReview(w: String) = w in review
    fun reviewList(): List<String> = review.toList()
    fun reviewCount() = review.size
    fun knownCount() = known.size

    fun markKnown(w: String) { known.add(w); review.remove(w); save() }
    fun markReview(w: String) { review.add(w); known.remove(w); save() }

    private fun save() {
        prefs.edit()
            .putStringSet("known", HashSet(known))
            .putString("review", review.joinToString("\n"))
            .apply()
    }
}
