package com.yad.guitar

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * GuitarView — visual gitar dengan 6 senar, fret, dan chord indicator.
 * User bisa tap senar untuk memetik.
 */
class GuitarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 6 senar
    private val stringColors = intArrayOf(
        Color.parseColor("#E5C07B"),  // E (bass) — kuning emas
        Color.parseColor("#D19A66"),  // A
        Color.parseColor("#C678DD"),  // D
        Color.parseColor("#61AFEF"),  // G
        Color.parseColor("#98C379"),  // B
        Color.parseColor("#E06C75"),  // E (treble) — merah
    )

    private val stringThicknesses = floatArrayOf(6f, 5f, 4.5f, 4f, 3.5f, 3f)

    // Paint
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A2E")
    }
    private val fretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        strokeWidth = 4f
    }
    private val fretNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }
    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 8f
        isAntiAlias = true
    }
    private val chordDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B5CF6")
    }
    private val chordTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Flash state
    private val flashAlpha = FloatArray(6) { 0f }
    private val flashHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Chord aktif
    private var currentChord: Chord? = null

    // Touch
    private var lastPluckedString = -1

    // Listener
    var onStringPlucked: ((Int) -> Unit)? = null

    fun setChord(chord: Chord?) {
        currentChord = chord
        invalidate()
    }

    fun flashString(index: Int) {
        if (index < 0 || index >= 6) return
        flashAlpha[index] = 1f
        invalidate()
        flashHandler.postDelayed({
            flashAlpha[index] = 0f
            invalidate()
        }, 200)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Nut (di kiri)
        val nutX = w * 0.08f
        val nutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0F0F0")
        }
        canvas.drawRect(nutX - 8f, h * 0.1f, nutX + 8f, h * 0.9f, nutPaint)

        // Fret lines (vertikal)
        val fretCount = 5
        val fretStartX = nutX
        val fretEndX = w * 0.95f
        val fretSpacing = (fretEndX - fretStartX) / (fretCount + 1)

        for (i in 1..fretCount) {
            val x = fretStartX + fretSpacing * i
            canvas.drawLine(x, h * 0.1f, x, h * 0.9f, fretPaint)

            // Fret number
            canvas.drawText(
                i.toString(),
                x - fretSpacing / 2,
                h * 0.95f,
                fretNumberPaint
            )
        }

        // Strings (horizontal)
        val stringTopY = h * 0.15f
        val stringBottomY = h * 0.85f
        val stringSpacing = (stringBottomY - stringTopY) / 5

        for (i in 0 until 6) {
            val y = stringTopY + stringSpacing * i
            stringPaint.color = stringColors[i]
            stringPaint.strokeWidth = stringThicknesses[i]

            // Normal string
            canvas.drawLine(nutX, y, w * 0.98f, y, stringPaint)

            // Flash overlay
            if (flashAlpha[i] > 0f) {
                flashPaint.alpha = (flashAlpha[i] * 255).toInt()
                canvas.drawLine(nutX, y, w * 0.98f, y, flashPaint)
            }
        }

        // Chord indicator — dot di fret yang ditekan
        currentChord?.let { chord ->
            for (stringIdx in 0 until 6) {
                val fret = chord.frets[stringIdx]
                if (fret > 0 && fret <= fretCount) {
                    val y = stringTopY + stringSpacing * stringIdx
                    val x = nutX + fretSpacing * (fret - 0.5f)

                    // Dot
                    canvas.drawCircle(x, y, 18f, chordDotPaint)

                    // Finger number
                    val finger = chord.fingers[stringIdx]
                    if (finger > 0) {
                        canvas.drawText(finger.toString(), x, y + 10f, chordTextPaint)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val stringIdx = getStringIndexFromY(event.y)
                if (stringIdx >= 0 && stringIdx != lastPluckedString) {
                    lastPluckedString = stringIdx
                    onStringPlucked?.invoke(stringIdx)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val stringIdx = getStringIndexFromY(event.y)
                if (stringIdx >= 0 && stringIdx != lastPluckedString) {
                    lastPluckedString = stringIdx
                    onStringPlucked?.invoke(stringIdx)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastPluckedString = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getStringIndexFromY(y: Float): Int {
        val h = height.toFloat()
        val stringTopY = h * 0.15f
        val stringBottomY = h * 0.85f
        val stringSpacing = (stringBottomY - stringTopY) / 5

        for (i in 0 until 6) {
            val stringY = stringTopY + stringSpacing * i
            if (Math.abs(y - stringY) < stringSpacing / 2) {
                return i
            }
        }
        return -1
    }
}
