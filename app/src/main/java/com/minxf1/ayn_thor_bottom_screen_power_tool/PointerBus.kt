package com.minxf1.ayn_thor_bottom_screen_power_tool

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference

data class CursorState(val x: Float, val y: Float, val displayW: Int, val displayH: Int)
data class ScrollIndicator(val dirX: Int, val dirY: Int, val atMs: Long)

object PointerBus {
    private val stateRef = AtomicReference(CursorState(200f, 200f, 1080, 1920))
    private val scrollRef = AtomicReference(ScrollIndicator(0, 0, 0L))

    fun get(): CursorState = stateRef.get()

    fun setDisplaySize(w: Int, h: Int) {
        val s = stateRef.get()
        stateRef.set(s.copy(displayW = w, displayH = h, x = s.x.coerceIn(0f, (w - 1).toFloat()), y = s.y.coerceIn(0f, (h - 1).toFloat())))
    }

    fun computeMoveBy(dx: Float, dy: Float): Pair<Float, Float> {
        val s = stateRef.get()
        val nx = (s.x + dx).coerceIn(0f, (s.displayW - 1).toFloat())
        val ny = (s.y + dy).coerceIn(0f, (s.displayH - 1).toFloat())
        return Pair(nx, ny)
    }

    fun moveBy(dx: Float, dy: Float) {
        val s = stateRef.get()
        val (nx, ny) = computeMoveBy(dx, dy)
        stateRef.set(s.copy(x = nx, y = ny))
    }

    fun set(x: Float, y: Float) {
        val s = stateRef.get()
        stateRef.set(
            s.copy(
                x = x.coerceIn(0f, (s.displayW - 1).toFloat()),
                y = y.coerceIn(0f, (s.displayH - 1).toFloat())
            )
        )
    }

    fun setScrollIndicator(dirX: Int, dirY: Int) {
        scrollRef.set(ScrollIndicator(dirX, dirY, SystemClock.uptimeMillis()))
    }

    fun getScrollIndicator(): ScrollIndicator = scrollRef.get()
}
