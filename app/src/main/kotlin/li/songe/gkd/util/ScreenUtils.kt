package li.songe.gkd.util

import androidx.compose.ui.unit.IntSize
import li.songe.gkd.app


object ScreenUtils {
    fun getScreenSize(): IntSize {
        val b = app.windowManager.currentWindowMetrics.bounds
        return IntSize(b.width(), b.height())
    }

    fun getScreenWidth(): Int = getScreenSize().width

    fun getScreenHeight(): Int = getScreenSize().height

    fun inScreen(x: Float, y: Float): Boolean {
        val (w, h) = getScreenSize()
        return 0 <= x && 0 <= y && x <= w && y <= h
    }
}