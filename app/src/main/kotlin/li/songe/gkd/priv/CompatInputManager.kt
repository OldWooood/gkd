package li.songe.gkd.priv

import android.content.Context
import android.hardware.input.IInputManager
import priv.kit.core.binder.PrivilegeBinderWrapper

class CompatInputManager {
    val value: IInputManager = IInputManager.Stub.asInterface(
        requireNotNull(
            PrivilegeBinderWrapper.fromSystemService(Context.INPUT_SERVICE),
        ),
    )

    fun tap(x: Float, y: Float, duration: Long = 0): Boolean {
        return if (duration > 0) {
            swipe(x, y, x, y, duration)
        } else {
            value.asBinder().shellCommand("tap", x.toString(), y.toString()).ok
        }
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        return value.asBinder().shellCommand(
            "swipe",
            x1.toString(),
            y1.toString(),
            x2.toString(),
            y2.toString(),
            duration.toString(),
        ).ok
    }

    fun keyevent(keyCode: Int): Boolean {
        return value.asBinder().shellCommand("keyevent", keyCode.toString()).ok
    }
}
