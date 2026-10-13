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
            value.asBinder().shellCommand(
                "swipe",
                x.toString(),
                y.toString(),
                x.toString(),
                y.toString(),
                duration.toString(),
            ).ok
        } else {
            value.asBinder().shellCommand("tap", x.toString(), y.toString()).ok
        }
    }
}
