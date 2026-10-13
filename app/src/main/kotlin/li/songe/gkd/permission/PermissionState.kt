package li.songe.gkd.permission

import android.Manifest
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.pm.PermissionInfo
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.updateAllAppInfo
import li.songe.gkd.util.updateAppMutex

class PermissionState(
    val name: String,
    private val check: () -> Boolean,
    private val onChanged: (() -> Unit)? = null,
) {
    val stateFlow = MutableStateFlow(false)
    val value get() = stateFlow.value

    fun updateAndGet(): Boolean {
        return stateFlow.updateAndGet { check() }
    }

    fun refresh(): Boolean {
        val oldValue = value
        val newValue = updateAndGet()
        if (oldValue != newValue) {
            onChanged?.invoke()
        }
        return newValue
    }
}

private fun checkAllowedOp(op: String): Boolean = app.appOpsManager.checkOpNoThrow(
    op,
    android.os.Process.myUid(),
    app.packageName
).let {
    it != AppOpsManager.MODE_IGNORED && it != AppOpsManager.MODE_ERRORED
}

private const val installedAppsPermissionName = "com.android.permission.GET_INSTALLED_APPS"

// 仅部分机型(OEM)把「读取应用列表」做成运行时权限；不是运行时权限时视为已授权
private val installedAppsRuntimePermission: Boolean by lazy {
    runCatching {
        val info = app.packageManager.getPermissionInfo(installedAppsPermissionName, 0)
        (info.protection and PermissionInfo.PROTECTION_MASK_BASE) ==
            PermissionInfo.PROTECTION_DANGEROUS
    }.getOrDefault(false)
}


object PermissionStates {
    // https://github.com/orgs/gkd-kit/discussions/1234
    private fun checkAccessA11y(): Boolean {
        return checkAllowedOp(AppOpsManagerHidden.OPSTR_ACCESS_ACCESSIBILITY)
    }

    val Manifest_permission_GET_APP_OPS_STATS get() = "android.permission.GET_APP_OPS_STATS"

    private var canRestrictsRead = true
    private fun checkAccessRestrictedSettings(): Boolean {
        return if (
            canRestrictsRead &&
            app.checkGrantedPermission(Manifest_permission_GET_APP_OPS_STATS)
        ) {
            try {
                // https://cs.android.com/android/platform/superproject/+/android-14.0.0_r55:frameworks/base/services/core/java/com/android/server/appop/AppOpsService.java;l=4237
                checkAllowedOp(AppOpsManagerHidden.OPSTR_ACCESS_RESTRICTED_SETTINGS)
            } catch (_: SecurityException) {
                // https://cs.android.com/android/platform/superproject/+/android-14.0.0_r54:frameworks/base/services/core/java/com/android/server/appop/AppOpsService.java;l=4227
                canRestrictsRead = false
                true
            }
        } else {
            true
        }
    }

    private val appOpsAllowed by lazy {
        PermissionState(
            name = "启动相关操作权限",
            check = {
                val accessA11yAllowed = checkAccessA11y()
                val accessRestrictedSettingsAllowed = checkAccessRestrictedSettings()
                accessA11yAllowed && accessRestrictedSettingsAllowed
            },
        )
    }

    val appOpsRestrictedFlow by lazy {
        appOpsAllowed.stateFlow.map { !it }
            .stateIn(appScope, SharingStarted.Eagerly, false)
    }

    val localNetwork by lazy {
        PermissionState(
            name = "访问本地网络权限",
            check = {
                !AndroidTarget.CINNAMON_BUN ||
                    app.checkGrantedPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
            },
        )
    }

    val queryPackages by lazy {
        PermissionState(
            name = "读取应用列表权限",
            check = {
                // 该权限仅在部分机型上是运行时权限；不是运行时权限时视为已授权
                !installedAppsRuntimePermission ||
                    app.checkGrantedPermission(installedAppsPermissionName)
            },
            onChanged = {
                if (!updateAppMutex.mutex.isLocked) {
                    updateAllAppInfo()
                }
            },
        )
    }

    val drawOverlays by lazy {
        PermissionState(
            name = "悬浮窗权限",
            check = {
                // https://developer.android.com/security/fraud-prevention/activities?hl=zh-cn#hide_overlay_windows
                Settings.canDrawOverlays(app)
            },
        )
    }

    val writeSecureSettings by lazy {
        PermissionState(
            name = "写入安全设置权限",
            check = { app.checkGrantedPermission(Manifest.permission.WRITE_SECURE_SETTINGS) },
        )
    }

    val all by lazy {
        listOf(
            localNetwork,
            appOpsAllowed,
            drawOverlays,
            writeSecureSettings,
            queryPackages,
        )
    }

    fun refreshAll() {
        all.forEach {
            it.refresh()
        }
    }
}
