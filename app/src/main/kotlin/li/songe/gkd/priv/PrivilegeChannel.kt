package li.songe.gkd.priv

import android.os.IBinder
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 官方 Shizuku 通道：系统服务 binder 外层包一层, 事务由 Shizuku 进程以 shell/root 身份执行。
 *
 * @see ShizukuBinderWrapper
 */
fun shizukuSystemService(name: String): IBinder {
    val binder = SystemServiceHelper.getSystemService(name)
        ?: throw IllegalStateException("系统服务 $name 不可用")
    return ShizukuBinderWrapper(binder)
}
