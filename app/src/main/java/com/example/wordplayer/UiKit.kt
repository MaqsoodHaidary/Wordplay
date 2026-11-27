package com.example.wordplayer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object C {
    val BG = 0xFF0F1226.toInt()
    val SURFACE = 0xFF1A1E3A.toInt()
    val SURFACE2 = 0xFF252B55.toInt()
    val PRIMARY = 0xFF7C6CFF.toInt()
    val GREEN = 0xFF4ADE80.toInt()
    val ORANGE = 0xFFFFB74D.toInt()
    val TEXT = 0xFFFFFFFF.toInt()
    val MUTED = 0xFF9AA0C8.toInt()
    val BOLD: Typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    val MED: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.rounded(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dpf(radiusDp.toFloat())
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

fun ripple(bg: Drawable): RippleDrawable = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null)

fun Context.icon(res: Int, tint: Int, sizeDp: Int): ImageView = ImageView(this).apply {
    setImageResource(res)
    setColorFilter(tint)
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

/** Rounded button with an icon and a label. */
fun Context.pillButton(
    label: String, iconRes: Int, bg: Int, fg: Int, strokeColor: Int = 0, onClick: () -> Unit
): LinearLayout {
    val ctx = this
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(15), dp(18), dp(15))
        background = ripple(rounded(bg, 16, strokeColor, if (strokeColor != 0) 2 else 0))
        isClickable = true
        addView(icon(iconRes, fg, 22))
        addView(TextView(ctx).apply {
            text = label
            setTextColor(fg)
            textSize = 16f
            typeface = C.MED
            setPadding(dp(10), 0, 0, 0)
        })
        setOnClickListener { onClick() }
    }
}

class MaxHeightScrollView(ctx: Context, private val maxH: Int) : ScrollView(ctx) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
    }
}
