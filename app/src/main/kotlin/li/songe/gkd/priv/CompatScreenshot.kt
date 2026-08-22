package li.songe.gkd.priv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import android.window.ScreenCapture
import android.window.ScreenCaptureInternal

object CompatScreenshot {
    fun capture(
        context: Context,
        windowManager: IWindowManager,
    ): Bitmap? {
        val display = context.getSystemService(DisplayManager::class.java)
            .getDisplay(DEFAULT_DISPLAY) ?: return null
        val displaySize = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(displaySize)
        val crop = Rect(0, 0, displaySize.x, displaySize.y)
        return captureByWindowManager(windowManager, crop)?.apply {
            setHasAlpha(false)
        }
    }

    private fun captureByWindowManager(
        windowManager: IWindowManager,
        crop: Rect,
    ): Bitmap? {
        return if (useScreenCaptureInternal) {
            captureByWindowManagerInternal(windowManager, crop)
        } else {
            val captureArgs = ScreenCapture.CaptureArgs.Builder()
                .setSourceCrop(crop)
                .build()
            val listener = ScreenCapture.createSyncCaptureListener()
            windowManager.captureDisplay(DEFAULT_DISPLAY, captureArgs, listener)
            listener.buffer?.let(::copyToSoftwareBitmap)
        }
    }

    private fun captureByWindowManagerInternal(
        windowManager: IWindowManager,
        crop: Rect,
    ): Bitmap? {
        val captureArgs = ScreenCaptureInternal.CaptureArgs.Builder()
            .setSourceCrop(crop)
            .build()
        val listener = ScreenCaptureInternal.createSyncCaptureListener()
        windowManager.captureDisplay(DEFAULT_DISPLAY, captureArgs, listener)
        return listener.buffer?.let(::copyToSoftwareBitmap)
    }

    private fun copyToSoftwareBitmap(
        buffer: ScreenCapture.ScreenshotHardwareBuffer,
    ): Bitmap? {
        val screenshot = buffer.asBitmap() ?: return null
        return try {
            buffer.hardwareBuffer.use {
                screenshot.copy(Bitmap.Config.ARGB_8888, false)
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun copyToSoftwareBitmap(
        buffer: ScreenCaptureInternal.ScreenshotHardwareBuffer,
    ): Bitmap? {
        val screenshot = buffer.asBitmap() ?: return null
        return try {
            buffer.hardwareBuffer.use {
                screenshot.copy(Bitmap.Config.ARGB_8888, false)
            }
        } finally {
            screenshot.recycle()
        }
    }

    private val useScreenCaptureInternal by lazy {
        HiddenApiDetect.detectHiddenClass(
            "android.window.ScreenCaptureInternal",
        )
    }
}
