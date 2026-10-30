package com.quicklauncher.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Live preview in the settings screen. Uses the very same PopupRenderer as the real overlay. */
class PreviewView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val main = PopupRenderer(ctx) { invalidate() }
    private val ring = PopupRenderer(ctx) { invalidate() }.also { it.colsOverride = 2 }
    private var st = AppState()
    private var ringMode = false
    private var px = Float.NaN; private var py = Float.NaN
    private var cardL = 0f; private var cardT = 0f; private var cardScale = 1f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tag = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f * d; color = 0x80FFFFFF.toInt(); isFakeBoldText = true }
    private val rf = RectF()
    private val path = Path()

        fun bind(s: AppState, ringMode: Boolean) {
            st = s; this.ringMode = ringMode
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = st.settings
        rf.set(0f, 0f, width.toFloat(), height.toFloat())
        p.reset(); p.isAntiAlias = true; p.color = 0xFF080808.toInt()
        c.drawRoundRect(rf, 20 * d, 20 * d, p)
        if (ringMode) drawRing(c, s) else drawPhone(c, s)
    }

    private fun drawPhone(c: Canvas, s: Settings) {
        val m = resources.displayMetrics
        val sw = m.widthPixels / d; val sh = m.heightPixels / d
        val phoneH = height * 0.9f; val phoneW = phoneH * sw / sh
        val scale = phoneH / sh
        val l = (width - phoneW) / 2f; val t = (height - phoneH) / 2f + 6 * d
        rf.set(l, t, l + phoneW, t + phoneH)
        p.reset(); p.isAntiAlias = true; p.color = 0xFF000000.toInt(); c.drawRoundRect(rf, 24 * d, 24 * d, p)
        c.save()
        path.reset(); path.addRoundRect(rf, 24 * d, 24 * d, Path.Direction.CW); c.clipPath(path)
        c.save()
        c.translate(l, t); c.scale(scale, scale)
        val fullTrig = s.trigH >= sh - 54f
        val th = if (fullTrig) sh else s.trigH
        val trigTop = if (fullTrig) 0f else (sh * s.trigY / 100f - th / 2f).coerceIn(36f, max(36f, sh - th - 18f))
        val landscapeHide = s.disableInLandscape && m.widthPixels > m.heightPixels
        if (s.triggerEnabled && !landscapeHide) {
            p.reset(); p.isAntiAlias = true
            p.color = android.graphics.Color.argb(((if (s.hideTrigger) 0f else s.trigOpacity / 100f) * 255).toInt().coerceIn(0, 255), 255, 255, 255)
            val tw = max(s.trigW, 3f)
            if (s.isLeft) c.drawRoundRect(-6f, trigTop, tw, trigTop + th, 4f, 4f, p)
            else c.drawRoundRect(sw - tw, trigTop, sw + 6f, trigTop + th, 4f, 4f, p)
        }
        val ps = s.forPopup()
        val pw = if (s.immersive) sw else s.w.coerceIn(180f, max(180f, sw - 12f)).coerceAtMost(sw - 12f)
        val ph = if (s.immersive) sh - 36f - 12f else s.h.coerceIn(180f, max(180f, sh - 70f))
        val popTop = if (s.immersive) 36f else (trigTop + th / 2 - ph / 2).coerceIn(36f, max(36f, sh - ph - 12f))
        main.colsOverride = null
        main.wDp = pw; main.hDp = ph; main.edit = false; main.hoverId = null
        main.cardBg = !s.immersive
        main.setData(ps, st.items)
                if (s.immersive) {
            p.reset(); p.color = android.graphics.Color.argb((ps.glass / 100f * 255).toInt(), 0, 0, 0)
            c.drawRect(0f, 0f, sw, sh, p)
        }
        c.translate(if (s.immersive) 0f else if (s.isLeft) 12f else sw - pw - 12f, popTop)
        main.draw(c)
        c.restore()   // content transform
        c.restore()   // phone clip
        rf.set(l, t, l + phoneW, t + phoneH)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = 0x2EFFFFFF
        c.drawRoundRect(rf, 24 * d, 24 * d, p)
        drawTag(c, "LIVE PREVIEW · ${(scale * 100).roundToInt().coerceAtLeast(1)}%")
    }

    private fun drawTag(c: Canvas, text: String) {
        tag.textAlign = Paint.Align.LEFT; tag.textSize = 10f * d
        val tw = tag.measureText(text)
        rf.set(12 * d, 8 * d, 12 * d + tw + 16 * d, 8 * d + 20 * d)
        p.reset(); p.isAntiAlias = true; p.color = 0x66000000
        c.drawRoundRect(rf, 6 * d, 6 * d, p)
        c.drawText(text, rf.left + 8 * d, rf.centerY() - (tag.ascent() + tag.descent()) / 2, tag)
    }

    private fun drawRing(c: Canvas, s: Settings) {
        val sample = st.items.sortedBy { it.slot }.take(4)
        val cols = max(1, s.cols)
        val colW = max(30f, (s.w - 2 * s.pad - (cols - 1) * s.cgap) / cols)
        val cw = colW * 2 + s.cgap + 2 * s.pad
        val rows = max(1, (sample.size + 1) / 2)
        val ch = 2 * s.pad + rows * (s.icon + 20f) + (rows - 1) * s.rgap
        cardScale = min(d, (height - 26 * d) / ch)
        cardL = (width - cw * cardScale) / 2f; cardT = (height - ch * cardScale) / 2f + 8 * d
        ring.wDp = cw; ring.hDp = ch; ring.edit = false
        ring.setData(s, sample)
        c.save(); c.translate(cardL, cardT); c.scale(cardScale, cardScale)
        if (px.isNaN() && sample.isNotEmpty()) { px = ring.centerX(0); py = ring.iconCenterY(0) }
        ring.hoverId = ring.nearest(px, py, s.icon / 2 + 16f)?.id
        ring.draw(c)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE
        p.strokeWidth = s.ringThick; p.color = android.graphics.Color.argb((s.ringOpacity / 100f * 255).toInt(), 255, 255, 255)
        c.drawCircle(px, py, s.ringSize / 2f, p)
        c.restore()
        drawTag(c, "SELECTION RING PREVIEW")
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!ringMode) return false
        px = (e.x - cardL) / cardScale; py = (e.y - cardT) / cardScale
        if (e.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        invalidate(); return true
    }
}

/** Single tile rendered with the popup renderer (used in lists and the editor). */
class TileView(ctx: Context, private val sizeDp: Int) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val r = PopupRenderer(ctx) { invalidate() }.also { it.colsOverride = 1; it.cardBg = false }
    var item: Item? = null
    override fun onDraw(c: Canvas) {
        val i = item ?: return
        c.scale(d, d)
        r.wDp = sizeDp.toFloat(); r.hDp = sizeDp.toFloat()
        r.setData(Settings(icon = sizeDp.toFloat(), showLbl = false, pad = 0f, cols = 1), listOf(i))
        r.draw(c)
    }
}
