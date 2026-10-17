package li.songe.gkd.priv.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.app
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.priv.PrivilegeContext
import li.songe.gkd.priv.PrivilegeStartupException
import li.songe.gkd.priv.clearPrivilegeContext
import li.songe.gkd.priv.privilegeContextFlow
import li.songe.gkd.priv.privilegeDesiredEnabledFlow
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.toast
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class ShizukuState(
    val available: Boolean,
    val authorized: Boolean,
    val uid: Int?,
    val version: Int?,
    val message: String,
)

object ShizukuPrivilegeController {
    private const val SHIZUKU_PERMISSION_REQUEST_CODE = 42
    private val connectMutex = Mutex()

    fun state(): ShizukuState {
        if (!Shizuku.pingBinder()) {
            return ShizukuState(
                available = false,
                authorized = false,
                uid = null,
                version = null,
                message = "Shizuku 未运行",
            )
        }
        if (Shizuku.isPreV11()) {
            return ShizukuState(
                available = false,
                authorized = false,
                uid = null,
                version = Shizuku.getVersion(),
                message = "不支持 Shizuku v11 以下版本",
            )
        }
        val version = Shizuku.getVersion()
        val uid = Shizuku.getUid().takeIf { it >= 0 }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return ShizukuState(
                available = true,
                authorized = true,
                uid = uid,
                version = version,
                message = "Shizuku 已就绪",
            )
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            return ShizukuState(
                available = true,
                authorized = false,
                uid = uid,
                version = version,
                message = "Shizuku 授权已被拒绝，请在 Shizuku 应用中允许本应用",
            )
        }
        return ShizukuState(
            available = true,
            authorized = false,
            uid = uid,
            version = version,
            message = "需要 Shizuku 授权",
        )
    }

    suspend fun requestAuthorization(): ShizukuState {
        val current = state()
        if (!current.available || current.authorized || Shizuku.shouldShowRequestPermissionRationale()) {
            return current
        }
        return suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            fun finish(result: ShizukuState) {
                if (!completed.compareAndSet(false, true)) return
                Shizuku.removeRequestPermissionResultListener(listener)
                if (continuation.isActive) continuation.resume(result)
            }

            listener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
                if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                    finish(state())
                }
            }
            continuation.invokeOnCancellation {
                if (completed.compareAndSet(false, true)) {
                    Shizuku.removeRequestPermissionResultListener(listener)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    finish(state())
                } else {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } catch (throwable: Throwable) {
                finish(
                    current.copy(
                        message = throwable.message ?: throwable.javaClass.name,
                    ),
                )
            }
        }
    }

    suspend fun start(): PrivilegeContext = connectMutex.withLock {
        privilegeContextFlow.value?.let { return@withLock it }
        val current = state()
        if (!current.available) {
            throw PrivilegeStartupException(current.message)
        }
        if (!current.authorized) {
            throw PrivilegeStartupException("请先授权 Shizuku")
        }
        if (!app.justStarted) {
            toast("正在连接特权服务...")
        }
        val context = PrivilegeContext.create(
            shizukuUid = current.uid ?: -1,
            shizukuVersion = current.version ?: -1,
        )
        privilegeContextFlow.value = context
        privilegeDesiredEnabledFlow.value = true
        PermissionStates.refreshAll()
        toast("特权服务连接成功", delayMillis = if (app.justStarted) 1200L else 0L)
        context
    }

    fun stop() {
        privilegeDesiredEnabledFlow.value = false
        disconnect()
    }

    /**
     * Shizuku binder 死亡或用户主动停止时调用, 已授予的应用权限不受影响。
     */
    fun disconnect() {
        val context = privilegeContextFlow.value ?: return
        clearPrivilegeContext(context)
        toast("特权服务已断开")
    }

    suspend fun startSilently() {
        if (!privilegeDesiredEnabledFlow.value) return
        if (privilegeContextFlow.value != null) return
        val current = state()
        if (!current.available || !current.authorized) return
        try {
            start()
        } catch (e: Throwable) {
            LogUtils.d("silent privilege start failed", e)
        }
    }
}
