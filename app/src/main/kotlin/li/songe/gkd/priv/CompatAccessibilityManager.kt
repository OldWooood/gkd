package li.songe.gkd.priv

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.IAccessibilityServiceClient
import android.content.Context
import android.os.IBinder
import android.view.accessibility.IAccessibilityManager
import priv.kit.core.binder.PrivilegeBinderWrapper

class CompatAccessibilityManager {
    val value: IAccessibilityManager = IAccessibilityManager.Stub.asInterface(
        requireNotNull(
            PrivilegeBinderWrapper.fromSystemService(Context.ACCESSIBILITY_SERVICE),
        ),
    )

    fun registerUiTestAutomationService(
        owner: IBinder,
        client: IAccessibilityServiceClient,
        info: AccessibilityServiceInfo,
        userId: Int,
        flags: Int,
    ): Unit = value.registerUiTestAutomationService(owner, client, info, userId, flags)

    fun isUiAutomationRunning(): Boolean {
        val serviceDump = value.asBinder().dump()
        check(serviceDump.isNotBlank()) {
            "AccessibilityManagerService dump is empty"
        }
        return containsModernUiAutomation(serviceDump)
    }
}

private val uiAutomationDumpRegex = Regex("""\bUi Automation\[""")

fun containsModernUiAutomation(dump: String): Boolean {
    return uiAutomationDumpRegex.containsMatchIn(dump)
}
