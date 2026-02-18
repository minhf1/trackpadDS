package com.minxf1.ayn_thor_bottom_screen_power_tool

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.min

internal class CursorDotView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(ctx, attrs, defStyleAttr) {
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiConstants.Colors.CURSOR_DOT
        style = Paint.Style.FILL
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiConstants.Colors.CURSOR_INDICATOR
        style = Paint.Style.FILL
    }
    private var dirX = 0
    private var dirY = 0
    private var dotRadius = 0f
    private val leftPath = Path()
    private val rightPath = Path()
    private val upPath = Path()
    private val downPath = Path()

    // Constructor for programmatic sizing.
    constructor(ctx: Context, sizePx: Int) : this(ctx) {
        layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
    }

    // Updates scroll direction indicator and triggers redraw if changed.
    fun setScrollIndicator(dirX: Int, dirY: Int) {
        if (this.dirX == dirX && this.dirY == dirY) return
        this.dirX = dirX
        this.dirY = dirY
        invalidate()
    }

    fun setColors(dotColor: Int, indicatorColor: Int) {
        if (dotPaint.color == dotColor && indicatorPaint.color == indicatorColor) return
        dotPaint.color = dotColor
        indicatorPaint.color = indicatorColor
        invalidate()
    }

    // Draws the center dot and directional indicators.
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        canvas.drawCircle(cx, cy, dotRadius, dotPaint)

        if (dirX < 0) {
            canvas.drawPath(leftPath, indicatorPaint)
        } else if (dirX > 0) {
            canvas.drawPath(rightPath, indicatorPaint)
        }

        if (dirY < 0) {
            canvas.drawPath(upPath, indicatorPaint)
        } else if (dirY > 0) {
            canvas.drawPath(downPath, indicatorPaint)
        }
    }

    // Recomputes geometry for dot and indicator triangles when the view size changes.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        dotRadius = size * UiConstants.Cursor.DOT_RADIUS_RATIO
        val triSize = size * UiConstants.Cursor.TRI_SIZE_RATIO
        val half = size / 2f
        val offset = (half - triSize / 2f).coerceAtLeast(0f)

        leftPath.reset()
        leftPath.moveTo(cx - offset, cy)
        leftPath.lineTo(cx - offset + triSize, cy - triSize / 2f)
        leftPath.lineTo(cx - offset + triSize, cy + triSize / 2f)
        leftPath.close()

        rightPath.reset()
        rightPath.moveTo(cx + offset, cy)
        rightPath.lineTo(cx + offset - triSize, cy - triSize / 2f)
        rightPath.lineTo(cx + offset - triSize, cy + triSize / 2f)
        rightPath.close()

        upPath.reset()
        upPath.moveTo(cx, cy - offset)
        upPath.lineTo(cx - triSize / 2f, cy - offset + triSize)
        upPath.lineTo(cx + triSize / 2f, cy - offset + triSize)
        upPath.close()

        downPath.reset()
        downPath.moveTo(cx, cy + offset)
        downPath.lineTo(cx - triSize / 2f, cy + offset - triSize)
        downPath.lineTo(cx + triSize / 2f, cy + offset - triSize)
        downPath.close()
    }
}
