package com.example.wordplayer

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale
import kotlin.math.min

class MainActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var dict: Dict
    private lateinit var store: WordStore
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // screens
    private lateinit var homeScreen: View
    private lateinit var playerScreen: FrameLayout

    // home views
    private lateinit var tvLearned: TextView
    private lateinit var tvReview: TextView
    private lateinit var tvLeft: TextView
    private lateinit var tvProgressLabel: TextView
    private lateinit var tvSubStatus: TextView
    private lateinit var tvNow: TextView
    private lateinit var nowCard: View
    private lateinit var progressFill: View
    private lateinit var progressSpacer: View
    private lateinit var reviewHomeBtn: LinearLayout

    // player views
    private lateinit var playerView: PlayerView
    private lateinit var topBar: View
    private lateinit var bottomBox: LinearLayout
    private lateinit var controlsBar: View
    private lateinit var centerIcon: View
    private lateinit var subtitleView: TextView
    private lateinit var seek: SeekBar
    private lateinit var tvPos: TextView
    private lateinit var tvDur: TextView
    private lateinit var tvTitle: TextView
    private lateinit var chipCount: TextView
    private val bottomScrim = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0xCC000000.toInt())
    )

    // state
    private var cues: List<Cue> = emptyList()
    private var uniqueWords: Set<String> = emptySet()
    private var shownIdx = -2
    private var dragging = false
    private var hasVideo = false
    private var videoTitle = ""
    private var controlsShown = true
    private var lastInteraction = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val wordRe = Regex("[A-Za-z]+(?:['’][A-Za-z]+)*")

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            videoTitle = displayName(uri).substringBeforeLast('.')
            tvTitle.text = videoTitle
            hasVideo = true
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
            lastInteraction = SystemClock.elapsedRealtime()
            refreshStats()
            showPlayer()
        }
    }
    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadSubtitle(uri)
    }

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
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.US)
                ttsReady = r != null && r >= 0
            }
        }

        player = ExoPlayer.Builder(this).build()

        val root = FrameLayout(this).apply { setBackgroundColor(C.BG) }
        homeScreen = buildHome()
        playerScreen = buildPlayer()
        root.addView(homeScreen, FrameLayout.LayoutParams(-1, -1))
        root.addView(playerScreen, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (playerScreen.visibility == View.VISIBLE) showHome() else finish()
            }
        })

        refreshStats()
        handler.post(ticker)
    }

    // ───────────────────────── HOME ─────────────────────────

    private fun buildHome(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(C.BG); isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))

        // header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_launcher) },
            LinearLayout.LayoutParams(dp(56), dp(56)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        titles.addView(TextView(this).apply {
            text = "MKM English"; textSize = 28f; setTextColor(C.TEXT); typeface = C.BOLD
        })
        titles.addView(TextView(this).apply {
            text = "Learn English from what you watch"; textSize = 14f; setTextColor(C.MUTED)
        })
        header.addView(titles)
        col.addView(header)

        // stats card
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(C.SURFACE, 24)
            setPadding(dp(16), dp(16), dp(16), dp(18))
        }
        val statsTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statsTitle.addView(icon(R.drawable.ic_chart, C.PRIMARY, 22))
        statsTitle.addView(TextView(this).apply {
            text = "Your progress"; textSize = 17f; setTextColor(C.TEXT); typeface = C.BOLD
            setPadding(dp(10), 0, 0, 0)
        })
        stats.addView(statsTitle)

        fun tile(label: String, color: Int, iconRes: Int): Pair<View, TextView> {
            val value = TextView(this).apply {
                text = "0"; textSize = 26f; setTextColor(C.TEXT); typeface = C.BOLD
            }
            val t = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(C.SURFACE2, 18)
                setPadding(dp(12), dp(14), dp(12), dp(14))
                addView(icon(iconRes, color, 22))
                addView(value, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
                addView(TextView(this@MainActivity).apply {
                    text = label; textSize = 12f; setTextColor(C.MUTED)
                })
            }
            return Pair(t, value)
        }
        val t1 = tile("Words learned", C.GREEN, R.drawable.ic_school)
        val t2 = tile("To review", C.ORANGE, R.drawable.ic_refresh)
        val t3 = tile("Left in video", C.PRIMARY, R.drawable.ic_video)
        tvLearned = t1.second; tvReview = t2.second; tvLeft = t3.second
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tiles.addView(t1.first, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        tiles.addView(t2.first, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        tiles.addView(t3.first, LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(tiles, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        tvProgressLabel = TextView(this).apply { textSize = 13f; setTextColor(C.MUTED) }
        stats.addView(tvProgressLabel, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })

        progressFill = View(this).apply { background = rounded(C.PRIMARY, 6) }
        progressSpacer = View(this)
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 100f
            background = rounded(C.SURFACE2, 6)
        }
        track.addView(progressFill, LinearLayout.LayoutParams(0, -1, 0f))
        track.addView(progressSpacer, LinearLayout.LayoutParams(0, -1, 100f))
        stats.addView(track, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(8) })
        col.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })

        // now watching
        tvNow = TextView(this).apply {
            textSize = 18f; setTextColor(C.TEXT); typeface = C.MED
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val nowBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(C.SURFACE, 24)
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        nowBox.addView(TextView(this).apply {
            text = "NOW WATCHING"; textSize = 11f; setTextColor(C.MUTED); letterSpacing = 0.1f
        })
        nowBox.addView(tvNow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(12) })
        nowBox.addView(pillButton("Continue", R.drawable.ic_play, C.PRIMARY, C.TEXT) { showPlayer(); player.play() })
        nowBox.visibility = View.GONE
        nowCard = nowBox
        col.addView(nowBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        // actions
        col.addView(
            pillButton("Open video", R.drawable.ic_video, C.PRIMARY, C.TEXT) { pickVideo.launch(arrayOf("*/*")) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        col.addView(
            pillButton("Add subtitle", R.drawable.ic_subtitles, C.SURFACE, C.TEXT, C.SURFACE2) { pickSubtitle.launch(arrayOf("*/*")) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        reviewHomeBtn = pillButton("Review words (0)", R.drawable.ic_bookmark, C.SURFACE, C.ORANGE, C.SURFACE2) { showReview() }
        col.addView(reviewHomeBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        tvSubStatus = TextView(this).apply { textSize = 13f; setTextColor(C.MUTED); gravity = Gravity.CENTER }
        col.addView(tvSubStatus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        return scroll
    }

    private fun setProgress(pct: Float) {
        (progressFill.layoutParams as LinearLayout.LayoutParams).weight = pct
        (progressSpacer.layoutParams as LinearLayout.LayoutParams).weight = 100f - pct
        progressFill.requestLayout()
        progressSpacer.requestLayout()
    }

    private fun refreshStats() {
        val learned = store.knownCount()
        val review = store.reviewCount()
        tvLearned.text = learned.toString()
        tvReview.text = review.toString()
        chipCount.text = review.toString()
        (reviewHomeBtn.getChildAt(1) as TextView).text = "Review words ($review)"
        if (uniqueWords.isEmpty()) {
            tvLeft.text = "–"
            tvProgressLabel.text = "Add a subtitle to track this video's words"
            setProgress(0f)
        } else {
            val total = uniqueWords.size
            val known = uniqueWords.count { store.isKnown(it) }
            val pct = known * 100f / total
            tvLeft.text = (total - known).toString()
            tvProgressLabel.text = "$known of $total words in this video known (${pct.toInt()}%)"
            setProgress(pct)
        }
        tvSubStatus.text = if (cues.isEmpty()) "No subtitle loaded" else "Subtitle loaded: ${cues.size} lines"
        nowCard.visibility = if (hasVideo) View.VISIBLE else View.GONE
        tvNow.text = videoTitle
    }

    // ───────────────────────── PLAYER ─────────────────────────

    private fun iconButton(res: Int, onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
        background = ripple(GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x33FFFFFF) })
        addView(icon(res, Color.WHITE, 22), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun buildPlayer(): FrameLayout {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); visibility = View.GONE }

        playerView = PlayerView(this).apply {
            useController = false
            player = this@MainActivity.player
            setOnClickListener { onScreenTap() }
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        // big play icon (shown while paused)
        val ci = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x99000000.toInt()) }
            addView(icon(R.drawable.ic_play, Color.WHITE, 40), FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
            visibility = View.GONE
        }
        centerIcon = ci
        root.addView(ci, FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER))

        // top bar
        val tb = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(14), dp(28))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xCC000000.toInt(), 0x00000000))
        }
        tb.addView(iconButton(R.drawable.ic_back) { showHome() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        tvTitle = TextView(this).apply {
            textSize = 16f; setTextColor(Color.WHITE); typeface = C.MED
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), 0)
        }
        tb.addView(tvTitle, LinearLayout.LayoutParams(0, -2, 1f))
        tb.addView(iconButton(R.drawable.ic_subtitles) { pickSubtitle.launch(arrayOf("*/*")) },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
        chipCount = TextView(this).apply {
            textSize = 14f; setTextColor(C.ORANGE); typeface = C.BOLD; text = "0"
            setPadding(dp(6), 0, 0, 0)
        }
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(14), dp(10))
            background = ripple(rounded(0x33FFB74D, 22))
            isClickable = true
            addView(icon(R.drawable.ic_bookmark, C.ORANGE, 18))
            addView(chipCount)
            setOnClickListener { showReview() }
        }
        tb.addView(chip, LinearLayout.LayoutParams(-2, dp(44)))
        topBar = tb
        root.addView(tb, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // bottom: subtitle + seek bar
        bottomBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(36), dp(16), dp(10))
            background = bottomScrim
        }
        subtitleView = TextView(this).apply {
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            background = rounded(0xCC0F1226.toInt(), 14)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
            visibility = View.GONE
        }
        bottomBox.addView(subtitleView, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(10) })

        tvPos = TextView(this).apply { textSize = 12f; setTextColor(Color.WHITE); text = "0:00" }
        tvDur = TextView(this).apply { textSize = 12f; setTextColor(Color.WHITE); text = "0:00" }
        seek = SeekBar(this).apply {
            progressTintList = ColorStateList.valueOf(C.PRIMARY)
            thumbTintList = ColorStateList.valueOf(C.PRIMARY)
            progressBackgroundTintList = ColorStateList.valueOf(0x66FFFFFF)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) { player.seekTo(p.toLong()); lastInteraction = SystemClock.elapsedRealtime() }
                }
                override fun onStartTrackingTouch(s: SeekBar) { dragging = true }
                override fun onStopTrackingTouch(s: SeekBar) { dragging = false; lastInteraction = SystemClock.elapsedRealtime() }
            })
        }
        val cb = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cb.addView(tvPos)
        cb.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        cb.addView(tvDur)
        controlsBar = cb
        bottomBox.addView(cb, LinearLayout.LayoutParams(-1, -2))
        root.addView(bottomBox, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        return root
    }

    private fun setControls(show: Boolean) {
        if (show == controlsShown) return
        controlsShown = show
        val vis = if (show) View.VISIBLE else View.GONE
        topBar.visibility = vis
        controlsBar.visibility = vis
        bottomBox.background = if (show) bottomScrim else null
        if (show) {
            topBar.alpha = 0f
            topBar.animate().alpha(1f).setDuration(150).start()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (playerScreen.visibility == View.VISIBLE) {
                val pos = player.currentPosition
                val dur = player.duration
                if (!dragging && dur > 0) { seek.max = dur.toInt(); seek.progress = pos.toInt() }
                tvPos.text = fmt(pos)
                tvDur.text = if (dur > 0) fmt(dur) else "0:00"
                val idx = findCue(pos)
                if (idx != shownIdx) renderCue(idx)
                val playing = player.isPlaying
                centerIcon.visibility = if (!playing && hasVideo) View.VISIBLE else View.GONE
                setControls(!playing || SystemClock.elapsedRealtime() - lastInteraction < 2500)
            }
            handler.postDelayed(this, 100)
        }
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    private fun onScreenTap() {
        lastInteraction = SystemClock.elapsedRealtime()
        if (!hasVideo) { pickVideo.launch(arrayOf("*/*")); return }
        if (player.isPlaying) player.pause() else player.play()
    }

    private fun showPlayer() {
        homeScreen.visibility = View.GONE
        playerScreen.visibility = View.VISIBLE
        controlsShown = false
        setControls(true)
        setImmersive(true)
    }

    private fun showHome() {
        player.pause()
        playerScreen.visibility = View.GONE
        homeScreen.visibility = View.VISIBLE
        setImmersive(false)
        refreshStats()
    }

    private fun setImmersive(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ───────────────────────── SUBTITLES ─────────────────────────

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
            val key = normalize(m.value)
            val color = when {
                store.isKnown(key) -> C.GREEN
                store.isReview(key) -> C.ORANGE
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
            val set = HashSet<String>()
            for (c in cues) for (m in wordRe.findAll(c.text)) set.add(normalize(m.value))
            uniqueWords = set
            shownIdx = -2
            refreshStats()
            Toast.makeText(this, "${cues.size} subtitle lines loaded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not read subtitle: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: "Video"
            }
        } catch (e: Exception) { }
        return "Video"
    }

    // ───────────────────────── SHEETS ─────────────────────────

    private fun bottomSheet(content: View): Dialog {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(C.SURFACE)
                val r = dpf(28f)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(20), dp(10), dp(20), dp(20))
        }
        wrap.addView(View(this).apply { background = rounded(C.MUTED, 3) },
            LinearLayout.LayoutParams(dp(40), dp(5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(14) })
        wrap.addView(content, LinearLayout.LayoutParams(-1, -2))
        dlg.setContentView(wrap)
        val w = min(resources.displayMetrics.widthPixels, dp(600))
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.6f)
        }
        return dlg
    }

    private fun onWordTapped(key: String) {
        val wasPlaying = player.isPlaying
        player.pause()
        showWord(key, resumeOnClose = wasPlaying, onClosed = null)
    }

    private fun speak(w: String) {
        if (ttsReady) tts?.speak(w, TextToSpeech.QUEUE_FLUSH, null, "word")
        else Toast.makeText(this, "Speech is not available on this device", Toast.LENGTH_SHORT).show()
    }

    private fun showWord(key: String, resumeOnClose: Boolean, onClosed: (() -> Unit)?) {
        lateinit var dlg: Dialog
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(this).apply {
            text = key; textSize = 30f; setTextColor(C.TEXT); typeface = C.BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(iconButton(R.drawable.ic_volume) { speak(key) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        content.addView(head)

        val status: Pair<String, Int>? = when {
            store.isKnown(key) -> Pair("Known", C.GREEN)
            store.isReview(key) -> Pair("In your review list", C.ORANGE)
            else -> null
        }
        if (status != null) {
            content.addView(TextView(this).apply {
                text = status.first; textSize = 12f; setTextColor(status.second); typeface = C.MED
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = rounded((status.second and 0xFFFFFF) or 0x33000000, 12)
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        }

        val body = TextView(this).apply {
            textSize = 17f; setTextColor(0xFFE6E8FF.toInt())
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.START
            setLineSpacing(0f, 1.25f)
        }
        if (!dict.ready) {
            body.text = "The dictionary is still being prepared. Try again in a moment."
            body.textDirection = View.TEXT_DIRECTION_LTR
        } else {
            val entries = dict.lookup(key)
            if (entries.isEmpty()) {
                body.text = "No meaning found for this word."
                body.textDirection = View.TEXT_DIRECTION_LTR
            } else {
                val sb = SpannableStringBuilder()
                for (e in entries) {
                    if (sb.isNotEmpty()) sb.append("\n\n")
                    val s = sb.length
                    sb.append(e.head)
                    sb.setSpan(StyleSpan(Typeface.BOLD), s, sb.length, 0)
                    sb.append("\n").append(e.meaning)
                }
                body.text = sb
            }
        }
        val scroll = MaxHeightScrollView(this, (resources.displayMetrics.heightPixels * 0.4).toInt()).apply {
            background = rounded(C.SURFACE2, 16)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(body)
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = dp(16) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            pillButton("I don't know", R.drawable.ic_refresh, 0, C.ORANGE, C.ORANGE) { store.markReview(key); dlg.dismiss() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
        row.addView(
            pillButton("I know", R.drawable.ic_check, C.GREEN, 0xFF0B2E1A.toInt()) { store.markKnown(key); dlg.dismiss() },
            LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row)

        dlg = bottomSheet(content)
        dlg.setOnDismissListener {
            refreshStats()
            shownIdx = -2   // re-colour the subtitle
            if (resumeOnClose) player.play()
            onClosed?.invoke()
        }
        dlg.show()
    }

    private fun showReview() {
        player.pause()
        lateinit var dlg: Dialog
        val words = store.reviewList()
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(this).apply {
            text = "Review later"; textSize = 24f; setTextColor(C.TEXT); typeface = C.BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(this).apply {
            text = "${words.size} words"; textSize = 13f; setTextColor(C.ORANGE); typeface = C.MED
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = rounded(0x33FFB74D, 14)
        })
        content.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        if (words.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(28), dp(8), dp(28))
            }
            empty.addView(icon(R.drawable.ic_bookmark, C.MUTED, 40))
            empty.addView(TextView(this).apply {
                text = "No words yet.\nTap a word in the subtitle and press \"I don't know\" to save it here."
                textSize = 15f; setTextColor(C.MUTED); gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            })
            content.addView(empty)
        } else {
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for (w in words.reversed()) {
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = ripple(rounded(C.SURFACE2, 14))
                    setPadding(dp(16), dp(14), dp(12), dp(14))
                    isClickable = true
                    addView(TextView(this@MainActivity).apply {
                        text = w; textSize = 18f; setTextColor(C.TEXT); typeface = C.MED
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(icon(R.drawable.ic_chevron, C.MUTED, 22))
                    setOnClickListener {
                        dlg.dismiss()
                        showWord(w, resumeOnClose = false) { showReview() }
                    }
                }
                list.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
            val sc = MaxHeightScrollView(this, (resources.displayMetrics.heightPixels * 0.5).toInt())
            sc.addView(list)
            content.addView(sc, LinearLayout.LayoutParams(-1, -2))
        }
        dlg = bottomSheet(content)
        dlg.setOnDismissListener { refreshStats() }
        dlg.show()
    }

    override fun onStop() { super.onStop(); player.pause() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.shutdown()
        player.release()
        super.onDestroy()
    }
}
