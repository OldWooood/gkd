package li.songe.gkd.priv

import android.Manifest
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.LogUtils
import rikka.shizuku.Shizuku

class PrivilegeContext private constructor(
    val shizukuUid: Int,
    val shizukuVersion: Int,
) {
    private val packageManager = CompatPackageManager()
    private val activityManager = CompatActivityManager()
    private val appOpsService = CompatAppOpsService()
    private val inputManager = CompatInputManager()
    private var taskStackListenerRegistered = false

    private fun initialize() {
        activityManager.value.registerTaskStackListener(CompatTaskStackListener)
        taskStackListenerRegistered = true
        grantSelf()
    }

    fun destroy() {
        if (!taskStackListenerRegistered) return
        taskStackListenerRegistered = false
        try {
            activityManager.value.unregisterTaskStackListener(CompatTaskStackListener)
        } catch (e: Throwable) {
            // Shizuku 已断开时无法注销, 监听随进程消失
            LogUtils.d("unregisterTaskStackListener failed", e)
        }
    }

    fun grantSelf() {
        if (!canGrantRuntimePermissions) return
        allowAllSelfMode()
        allowAllSelfPermission()
    }

    fun topTask() = activityManager.getTasks().firstOrNull()
    fun topCpn() = topTask()?.topActivity

    fun getInstalledPackagesAsUser(
        flags: Int,
        userId: Int = currentUserId,
    ): List<PackageInfo> {
        return packageManager.appPackageManager.getInstalledPackagesAsUser(flags, userId)
    }

    fun tap(x: Float, y: Float, duration: Long = 0): Boolean {
        return inputManager.tap(x, y, duration)
    }

    // 与 priv-kit Privilege.isPermissionRestricted() 语义一致: 非 root 且服务端无授权能力时跳过授权
    private val canGrantRuntimePermissions by lazy {
        if (shizukuUid == Process.ROOT_UID) {
            true
        } else {
            try {
                Shizuku.checkRemotePermission(Manifest_permission_GRANT_RUNTIME_PERMISSIONS) ==
                        PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                LogUtils.d("checkRemotePermission failed", e)
                false
            }
        }
    }

    private fun setAllowSelfMode(code: Int) {
        val mode = appOpsService.value.checkOperation(code, Process.myUid(), META.appId)
        if (mode != AppOpsManager.MODE_ALLOWED) {
            appOpsService.value.setMode(
                code,
                Process.myUid(),
                META.appId,
                AppOpsManager.MODE_ALLOWED,
            )
        }
    }

    private fun allowAllSelfMode() {
        setAllowSelfMode(AppOpsManagerHidden.OP_SYSTEM_ALERT_WINDOW)
        setAllowSelfMode(AppOpsManagerHidden.OP_ACCESS_ACCESSIBILITY)
        setAllowSelfMode(AppOpsManagerHidden.OP_ACCESS_RESTRICTED_SETTINGS)
    }

    private fun grantSelfPermission(name: String) {
        if (app.checkGrantedPermission(name)) return
        try {
            packageManager.iPackageManager.grantRuntimePermission(META.appId, name, currentUserId)
        } catch (e: Throwable) {
            // 单个权限授予失败不应导致特权上下文创建失败
            LogUtils.d("grantSelfPermission failed", name, e)
        }
    }

    private fun allowAllSelfPermission() {
        if (!PermissionStates.queryPackages.value) {
            grantSelfPermission("com.android.permission.GET_INSTALLED_APPS")
        }
        grantSelfPermission(PermissionStates.Manifest_permission_GET_APP_OPS_STATS)
        grantSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
        if (AndroidTarget.CINNAMON_BUN) {
            grantSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    companion object {
        private const val Manifest_permission_GRANT_RUNTIME_PERMISSIONS =
            "android.permission.GRANT_RUNTIME_PERMISSIONS"

        fun create(shizukuUid: Int, shizukuVersion: Int): PrivilegeContext {
            val context = PrivilegeContext(shizukuUid, shizukuVersion)
            try {
                context.initialize()
            } catch (e: Throwable) {
                try {
                    context.destroy()
                } catch (cleanupError: Throwable) {
                    e.addSuppressed(cleanupError)
                }
                throw e
            }
            return context
        }
    }
}
