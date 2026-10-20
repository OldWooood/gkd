package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.a11y.A11yCommonImpl
import li.songe.gkd.a11y.A11yRuleEngine
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.a11y.updateTopActivity
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.priv.privilegeContextFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.store.updateEnableAutomator
import li.songe.gkd.ui.app.accessRestrictedSettingsShowFlow
import li.songe.gkd.util.DefaultA11yLifeImpl
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.OnA11yLife
import li.songe.gkd.util.componentName
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.runMainPost
import li.songe.gkd.util.toast
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("AccessibilityPolicy")
abstract class A11yService : AccessibilityService(), OnA11yLife by DefaultA11yLifeImpl(),
    A11yCommonImpl {
    override val windowNodeInfo: AccessibilityNodeInfo? get() = rootInActiveWindow

    override val ruleEngine by lazy { A11yRuleEngine(this) }

    override fun onCreate() = onCreated()
    override fun onServiceConnected() = onA11yConnected()
    override fun onInterrupt() {}
    override fun onDestroy() = onDestroyed()
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = ruleEngine.onA11yEvent(event)

    val startTime = System.currentTimeMillis()
    override var justStarted: Boolean = true
        get() {
            if (field) {
                field = System.currentTimeMillis() - startTime < 3_000
            }
            return field
        }

    override fun shutdown() {
        disableSelf()
    }

    private var destroyed = false
    private var connected = false

    val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)
        onA11yConnected { instance = this }
        onDestroyed { instance = null }
        onCreated {
            updateEnableAutomator(true)
        }
        onDestroyed {
            toast("无障碍已关闭")
            updateEnableAutomator(false)
        }
        useAliveOverlayView()
        onDestroyed {
            synchronized(topActivityFlow) {
                privilegeContextFlow.value?.run {
                    topCpn()?.let { cpn ->
                        // com.android.systemui
                        if (!topActivityFlow.value.sameAs(cpn.packageName, cpn.className)) {
                            updateTopActivity(
                                cpn.packageName,
                                cpn.className,
                                confirmedTop = true,
                            )
                        }
                    }
                }
            }
        }
        onDestroyed { destroyed = true }
        onA11yConnected {
            connected = true
            toast("无障碍已启动")
            ruleEngine.onA11yConnected()
        }
        onCreated {
            runMainPost(3000) {
                if (!(destroyed || connected)) {
                    toast("无障碍启动超时，请尝试关闭重启", forced = true)
                }
            }
        }
    }

    companion object {
        val a11yCn by lazy { SelectToSpeakService::class.componentName }
        val isRunning = MutableStateFlow(false)

        @Volatile
        var instance: A11yService? = null
            private set
    }
}

private fun A11yService.useAliveOverlayView() {
    val context = this
    var aliveView: View? = null
    fun removeA11View() {
        if (aliveView != null) {
            wm.removeView(aliveView)
            aliveView = null
        }
    }

    fun addA11View() {
        removeA11View()
        val tempView = View(context)
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags =
                flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            gravity = Gravity.START or Gravity.TOP
            width = 1
            height = 1
            packageName = context.packageName
        }
        try {
            // 某些设备 android.view.WindowManager$BadTokenException
            wm.addView(tempView, lp)
            aliveView = tempView
        } catch (e: Throwable) {
            aliveView = null
            LogUtils.d(e)
            toast("添加无障碍保活失败\n请尝试重启无障碍")
        }
    }
    onA11yConnected { addA11View() }
    onDestroyed { removeA11View() }
}

private val modifyA11yMutex = Mutex()
private const val A11Y_AWAIT_START_TIME = 2000L
private const val A11Y_AWAIT_FIX_TIME = 1000L

private fun modifyA11yRun(block: suspend () -> Unit) {
    if (modifyA11yMutex.isLocked) return
    appScope.launchTry(Dispatchers.IO) {
        if (modifyA11yMutex.isLocked) return@launchTry
        modifyA11yMutex.withLock { block() }
    }
}

private suspend fun switchA11yService() {
    if (A11yService.isRunning.value) {
        A11yService.instance?.disableSelf()
    } else {
        if (!PermissionStates.writeSecureSettings.updateAndGet()) {
            if (!PermissionStates.writeSecureSettings.value) {
                toast("请先授予「写入安全设置权限」")
                return
            }
        }
        val names = app.getSecureA11yServices()
        app.putSecureInt(Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        if (names.contains(A11yService.a11yCn)) { // 当前无障碍异常, 重启服务
            names.remove(A11yService.a11yCn)
            app.putSecureA11yServices(names)
            delay(A11Y_AWAIT_FIX_TIME.milliseconds)
        }
        names.add(A11yService.a11yCn)
        app.putSecureA11yServices(names)
        delay(A11Y_AWAIT_START_TIME.milliseconds)
        // https://github.com/orgs/gkd-kit/discussions/799
        if (!A11yService.isRunning.value) {
            toast("开启无障碍失败")
            accessRestrictedSettingsShowFlow.value = true
            return
        }
    }
}

fun switchAutomatorService() = modifyA11yRun {
    switchA11yService()
}

private suspend fun fixA11yService() {
    if (!A11yService.isRunning.value && PermissionStates.writeSecureSettings.updateAndGet()) {
        val names = app.getSecureA11yServices()
        val a11yBroken = names.contains(A11yService.a11yCn)
        if (a11yBroken) {
            // 无障碍出现故障, 重启服务
            names.remove(A11yService.a11yCn)
            app.putSecureA11yServices(names)
            // 必须等待一段时间, 否则概率不会触发系统重启无障碍
            delay(A11Y_AWAIT_FIX_TIME.milliseconds)
        }
        names.add(A11yService.a11yCn)
        app.putSecureA11yServices(names)
        delay(A11Y_AWAIT_START_TIME.milliseconds)
        if (!A11yService.isRunning.value) {
            toast("重启无障碍失败")
            accessRestrictedSettingsShowFlow.value = true
        }
    }
}

fun fixRestartAutomatorService() = modifyA11yRun {
    if (storeFlow.value.enableAutomator) {
        fixA11yService()
    }
}
