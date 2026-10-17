package li.songe.gkd.priv

import android.content.Context
import android.hardware.input.IInputManager
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import li.songe.gkd.util.LogUtils

// https://cs.android.com/android/platform/superproject/+/android-16.0.0_r1:cmds/input/src/com/android/commands/input/Input.java
private const val DEFAULT_DEVICE_ID = 0
private const val DEFAULT_SIZE = 1.0f
private const val DEFAULT_META_STATE = 0
private const val DEFAULT_PRECISION_X = 1.0f
private const val DEFAULT_PRECISION_Y = 1.0f
private const val DEFAULT_EDGE_FLAGS = 0
private const val DEFAULT_LONG_PRESS_DURATION = 300L

// InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
private const val INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2

/**
 * 触摸注入走 IInputManager.injectInputEvent,
 * 不能使用 IBinder.shellCommand: ShizukuBinderWrapper 不实现该隐藏接口方法。
 */
class CompatInputManager {
    private val value: IInputManager = IInputManager.Stub.asInterface(
        shizukuSystemService(Context.INPUT_SERVICE),
    )

    private val touchDeviceId by lazy {
        InputDevice.getDeviceIds().firstOrNull { deviceId ->
            InputDevice.getDevice(deviceId)?.supportsSource(InputDevice.SOURCE_TOUCHSCREEN) == true
        } ?: DEFAULT_DEVICE_ID
    }

    fun tap(x: Float, y: Float, duration: Long = 0): Boolean {
        return if (duration > 0) {
            longPress(x, y, duration)
        } else {
            val now = SystemClock.uptimeMillis()
            val downSuccess = injectMotionEvent(
                action = MotionEvent.ACTION_DOWN,
                downTime = now,
                eventTime = now,
                x = x,
                y = y,
                pressure = 1.0f,
            )
            val upSuccess = injectMotionEvent(
                action = MotionEvent.ACTION_UP,
                downTime = now,
                eventTime = now,
                x = x,
                y = y,
                pressure = 0.0f,
            )
            downSuccess && upSuccess
        }
    }

    private fun longPress(x: Float, y: Float, duration: Long): Boolean {
        val safeDuration = if (duration < 0) DEFAULT_LONG_PRESS_DURATION else duration
        val downTime = SystemClock.uptimeMillis()
        var success = injectMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            downTime = downTime,
            eventTime = downTime,
            x = x,
            y = y,
            pressure = 1.0f,
        )
        val endTime = downTime + safeDuration
        while (true) {
            val now = SystemClock.uptimeMillis()
            if (now >= endTime) break
            success = injectMotionEvent(
                action = MotionEvent.ACTION_MOVE,
                downTime = downTime,
                eventTime = now,
                x = x,
                y = y,
                pressure = 1.0f,
            ) && success
        }
        return injectMotionEvent(
            action = MotionEvent.ACTION_UP,
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            x = x,
            y = y,
            pressure = 0.0f,
        ) && success
    }

    private fun injectMotionEvent(
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float,
        y: Float,
        pressure: Float,
    ): Boolean {
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            x,
            y,
            pressure,
            DEFAULT_SIZE,
            DEFAULT_META_STATE,
            DEFAULT_PRECISION_X,
            DEFAULT_PRECISION_Y,
            touchDeviceId,
            DEFAULT_EDGE_FLAGS,
        )
        return try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            event.toHidden.setDisplayId(Display.DEFAULT_DISPLAY)
            injectInputEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun injectInputEvent(event: InputEvent): Boolean {
        return try {
            value.injectInputEvent(event, INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH)
        } catch (e: Throwable) {
            LogUtils.d("injectInputEvent failed", e)
            false
        }
    }
}
