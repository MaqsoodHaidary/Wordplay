package com.example.wordplayer

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var subtitleView: TextView
    private lateinit var seek: SeekBar
    private lateinit var reviewBtn: Button
    private lateinit var dict: Dict
    private lateinit var store: WordStore

    private var cues: List<Cue> = emptyList()
    private var shownIdx = -2
    private var dragging = false
    private val handler = Handler(Looper.getMainLooper())
    private val wordRe = Regex("[A-Za-z]+(?:['’][A-Za-z]+)*")

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
        }
    }
    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadSubtitle(uri)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        store = WordStore(this)
        dict = Dict(this)
        Thread {
            try { dict.open() } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Dictionary error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()

        player = ExoPlayer.Builder(this).build()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            useController = false
            player = this@MainActivity.player
            setOnClickListener { togglePlay() }
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        // top bar
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setBackgroundColor(0x66000000)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setOnClickListener { onClick() }
        }
        top.addView(btn("Video") { pickVideo.launch(arrayOf("*/*")) })
        top.addView(btn("Subtitle") { pickSubtitle.launch(arrayOf("*/*")) })
        reviewBtn = btn("Review (0)") { showReviewList() }
        top.addView(reviewBtn)
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // bottom: subtitle + seek bar
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        subtitleView = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
            visibility = View.GONE
        }
        val subLp = LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12); marginStart = dp(16); marginEnd = dp(16) }
        bottom.addView(subtitleView, subLp)
        seek = SeekBar(this).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) player.seekTo(p.toLong())
                }
                override fun onStartTrackingTouch(s: SeekBar) { dragging = true }
                override fun onStopTrackingTouch(s: SeekBar) { dragging = false }
            })
        }
        bottom.addView(seek, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        setContentView(root)
        updateReviewButton()
        handler.post(ticker)
    }

    private val ticker = object : Runnable {
        override fun run() {
            val pos = player.currentPosition
            val dur = player.duration
            if (!dragging && dur > 0) { seek.max = dur.toInt(); seek.progress = pos.toInt() }
            val idx = findCue(pos)
            if (idx != shownIdx) renderCue(idx)
            handler.postDelayed(this, 100)
        }
    }

    private fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }

    private fun findCue(pos: Long): Int {
        var lo = 0; var hi = cues.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = cues[mid]
            if (pos < c.start) hi = mid - 1 else if (pos >= c.end) lo = mid + 1 else return mid
        }
        return -1
    }

    private fun renderCue(idx: Int) {
        shownIdx = idx
        if (idx < 0) { subtitleView.visibility = View.GONE; subtitleView.text = ""; return }
        val text = cues[idx].text
        val sb = SpannableStringBuilder(text)
        for (m in wordRe.findAll(text)) {
            val raw = m.value
            val key = normalize(raw)
            val color = when {
                store.isKnown(key) -> 0xFF8BC34A.toInt()   // green = known
                store.isReview(key) -> 0xFFFFB74D.toInt()  // orange = review later
                else -> Color.WHITE
            }
            sb.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { onWordTapped(key) }
                override fun updateDrawState(ds: TextPaint) { ds.color = color; ds.isUnderlineText = false }
            }, m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        subtitleView.text = sb
        subtitleView.visibility = View.VISIBLE
    }

    private fun normalize(w: String) = w.lowercase().replace('’', '\'').trim('\'')

    private fun loadSubtitle(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            cues = SubtitleParser.parse(SubtitleParser.decode(bytes))
            shownIdx = -2
            Toast.makeText(this, "${cues.size} subtitle lines loaded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not read subtitle: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateReviewButton() { reviewBtn.text = "Review (${store.reviewCount()})" }

    private fun onWordTapped(key: String) {
        val wasPlaying = player.isPlaying
        player.pause()
        showWord(key, resumeOnClose = wasPlaying, onClosed = null)
    }

    private fun showWord(key: String, resumeOnClose: Boolean, onClosed: (() -> Unit)?) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1E1E1E.toInt())
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        col.addView(TextView(this).apply {
            text = key; textSize = 26f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        })

        val body = TextView(this).apply {
            textSize = 18f; setTextColor(0xFFE0E0E0.toInt())
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.START
            setPadding(0, dp(10), 0, dp(10))
        }
        if (!dict.ready) {
            body.text = "Dictionary is still being prepared, try again in a moment."
            body.textDirection = View.TEXT_DIRECTION_LTR
        } else {
            val entries = dict.lookup(key)
            if (entries.isEmpty()) {
                body.text = "No meaning found."
                body.textDirection = View.TEXT_DIRECTION_LTR
            } else {
                val sb = SpannableStringBuilder()
                for (e in entries) {
                    if (sb.isNotEmpty()) sb.append("\n\n")
                    val s = sb.length
                    sb.append(e.head)
                    sb.setSpan(android.text.style.StyleSpan(Typeface.BOLD), s, sb.length, 0)
                    sb.append("\n").append(e.meaning)
                }
                body.text = sb
            }
        }
        val scroll = ScrollView(this).apply { addView(body) }
        col.addView(scroll, LinearLayout.LayoutParams(-1, -2).apply { })
        // cap height
        scroll.post {
            val max = (resources.displayMetrics.heightPixels * 0.45).toInt()
            if (scroll.height > max) scroll.layoutParams = scroll.layoutParams.apply { height = max }
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dontKnow = Button(this).apply {
            text = "I don't know"; isAllCaps = false
            setOnClickListener { store.markReview(key); finishWord(dlg) }
        }
        val know = Button(this).apply {
            text = "I know"; isAllCaps = false
            setOnClickListener { store.markKnown(key); finishWord(dlg) }
        }
        row.addView(dontKnow, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(know, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(row)

        dlg.setContentView(col)
        dlg.setOnDismissListener {
            updateReviewButton()
            shownIdx = -2  // re-colour subtitle
            if (resumeOnClose) player.play()
            onClosed?.invoke()
        }
        dlg.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        dlg.show()
    }

    private fun finishWord(d: Dialog) = d.dismiss()

    private fun showReviewList() {
        val wasPlaying = player.isPlaying
        player.pause()
        val words = store.reviewList()
        val b = AlertDialog.Builder(this).setTitle("Review later (${words.size})")
        if (words.isEmpty()) {
            b.setMessage("No words yet. Tap a word in the subtitle and press \"I don't know\".")
        } else {
            b.setItems(words.toTypedArray()) { _, i ->
                showWord(words[i], resumeOnClose = false) { showReviewList() }
            }
        }
        b.setNegativeButton("Close", null)
        b.show()
    }

    override fun onStop() { super.onStop(); player.pause() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        super.onDestroy()
    }
}
