package li.songe.gkd.priv.shizuku

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import li.songe.gkd.priv.privilegeDesiredEnabledFlow
import li.songe.gkd.util.LogUtils
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeServerInfo
import priv.kit.core.PrivilegeStartupException
import priv.kit.core.internal.runtime.PrivilegeRuntimeStartCoordinator
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
    private const val START_TIMEOUT_MILLIS = 15_000L
    private val startMutex = Mutex()

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
        if (version < PrivilegeShizukuExternalStarter.SHIZUKU_USER_SERVICE_MIN_VERSION) {
            return ShizukuState(
                available = false,
                authorized = false,
                uid = uid,
                version = version,
                message = "当前 Shizuku 版本不支持 UserService",
            )
        }
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

    val isConnected: Boolean get() = Privilege.pingServer()

    suspend fun start(context: Context): PrivilegeServerInfo = startMutex.withLock {
        if (Privilege.pingServer()) {
            privilegeDesiredEnabledFlow.value = true
            return checkNotNull(Privilege.serverState.value) { "特权服务未连接" }
        }
        val current = state()
        if (!current.available) {
            throw PrivilegeStartupException(current.message)
        }
        if (!current.authorized) {
            throw PrivilegeStartupException("请先授权 Shizuku")
        }
        val coordinator = PrivilegeRuntimeStartCoordinator
        val preflight = coordinator.beginPreflight()
        val reconnectGrace = preflight.remainingReconnectGraceMillis
        if (reconnectGrace > 0) {
            delay(reconnectGrace)
        }
        val lease = coordinator.tryCommitClientStart(preflight)
            ?: throw PrivilegeStartupException("已有特权服务启动流程进行中")
        try {
            val launch = coordinator.beginClientLaunch(lease)
                ?: throw PrivilegeStartupException("无法创建特权服务启动请求")
            val commandLine = coordinator.createNativeStarterCommand(launch)
            PrivilegeShizukuExternalStarter(context).use { starter ->
                starter.start(commandLine)
            }
            val serverInfo = withTimeout(START_TIMEOUT_MILLIS) {
                Privilege.serverState.filterNotNull().first()
            }
            privilegeDesiredEnabledFlow.value = true
            return serverInfo
        } finally {
            lease.close()
        }
    }

    fun stop() {
        privilegeDesiredEnabledFlow.value = false
        if (Privilege.pingServer()) {
            Privilege.shutdownServer()
        }
    }

    suspend fun startSilently(context: Context) {
        if (!privilegeDesiredEnabledFlow.value) return
        if (Privilege.pingServer()) return
        if (!state().authorized) return
        try {
            start(context)
        } catch (e: Throwable) {
            LogUtils.d("silent privilege start failed", e)
        }
    }
}
