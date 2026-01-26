package com.example.ayn_thor_bottom_screen_power_tool

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.min

internal class CursorDotView(ctx: Context, sizePx: Int) : View(ctx) {
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00C853.toInt()
        style = Paint.Style.FILL
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00A844.toInt()
        style = Paint.Style.FILL
    }
    private var dirX = 0
    private var dirY = 0

    init {
        layoutParams = android.view.ViewGroup.LayoutParams(sizePx, sizePx)
    }

    fun setScrollIndicator(dirX: Int, dirY: Int) {
        if (this.dirX == dirX && this.dirY == dirY) return
        this.dirX = dirX
        this.dirY = dirY
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val dotRadius = size * 0.18f
        val triSize = size * 0.22f
        val half = size / 2f
        val offset = (half - triSize / 2f).coerceAtLeast(0f)

        canvas.drawCircle(cx, cy, dotRadius, dotPaint)

        if (dirX < 0) {
            canvas.drawPath(
                android.graphics.Path().apply {
                    moveTo(cx - offset, cy)
                    lineTo(cx - offset + triSize, cy - triSize / 2f)
                    lineTo(cx - offset + triSize, cy + triSize / 2f)
                    close()
                },
                indicatorPaint
            )
        } else if (dirX > 0) {
            canvas.drawPath(
                android.graphics.Path().apply {
                    moveTo(cx + offset, cy)
                    lineTo(cx + offset - triSize, cy - triSize / 2f)
                    lineTo(cx + offset - triSize, cy + triSize / 2f)
                    close()
                },
                indicatorPaint
            )
        }

        if (dirY < 0) {
            canvas.drawPath(
                android.graphics.Path().apply {
                    moveTo(cx, cy - offset)
                    lineTo(cx - triSize / 2f, cy - offset + triSize)
                    lineTo(cx + triSize / 2f, cy - offset + triSize)
                    close()
                },
                indicatorPaint
            )
        } else if (dirY > 0) {
            canvas.drawPath(
                android.graphics.Path().apply {
                    moveTo(cx, cy + offset)
                    lineTo(cx - triSize / 2f, cy + offset - triSize)
                    lineTo(cx + triSize / 2f, cy + offset - triSize)
                    close()
                },
                indicatorPaint
            )
        }
    }
}
