package li.songe.gkd.priv

import android.Manifest
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.pm.PackageInfo
import android.os.Process
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.util.AndroidTarget
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeServerInfo
import priv.kit.core.PrivilegeUserServiceConnection

class PrivilegeContext private constructor(
    val serverInfo: PrivilegeServerInfo,
    private val userServiceConnection: PrivilegeUserServiceConnection,
) {
    val serverLifecycleBinder = Privilege.getServerLifecycleBinder()
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

    suspend fun destroy() {
        try {
            if (taskStackListenerRegistered && Privilege.pingServer()) {
                activityManager.value.unregisterTaskStackListener(CompatTaskStackListener)
                taskStackListenerRegistered = false
            }
        } finally {
            userServiceConnection.unbind()
        }
    }

    fun grantSelf() {
        if (Privilege.isPermissionRestricted()) return
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
        Privilege.grantRuntimePermission(
            packageName = META.appId,
            permissionName = name,
        )
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
        suspend fun create(
            serverInfo: PrivilegeServerInfo,
            userServiceConnection: PrivilegeUserServiceConnection,
        ): PrivilegeContext {
            var context: PrivilegeContext? = null
            try {
                context = PrivilegeContext(serverInfo, userServiceConnection)
                context.initialize()
                return context
            } catch (e: Throwable) {
                try {
                    if (context == null) {
                        userServiceConnection.unbind()
                    } else {
                        context.destroy()
                    }
                } catch (cleanupError: Throwable) {
                    e.addSuppressed(cleanupError)
                }
                throw e
            }
        }
    }
}
