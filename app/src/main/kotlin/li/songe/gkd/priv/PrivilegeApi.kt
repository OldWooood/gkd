package li.songe.gkd.priv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.appScope
import li.songe.gkd.priv.shizuku.ShizukuPrivilegeController
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.launchTry
import rikka.shizuku.Shizuku

val currentUserId by lazy { android.os.Process.myUserHandle().hashCode() }

val privilegeContextFlow = MutableStateFlow<PrivilegeContext?>(null)

fun clearPrivilegeContext(context: PrivilegeContext) {
    if (!privilegeContextFlow.compareAndSet(context, null)) return
    try {
        context.destroy()
    } catch (e: Exception) {
        LogUtils.d("destroy PrivilegeContext failed", e)
    }
}

/**
 * 官方 Shizuku 是唯一特权通道: binder 出现时自动连接, binder 死亡时断开并回落纯无障碍模式。
 */
fun initPrivilege() {
    Shizuku.addBinderReceivedListenerSticky {
        appScope.launchTry(Dispatchers.IO) {
            ShizukuPrivilegeController.startSilently()
        }
    }
    Shizuku.addBinderDeadListener {
        ShizukuPrivilegeController.disconnect()
    }
}
