package li.songe.gkd.a11y

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import li.songe.gkd.META
import li.songe.gkd.data.ActionPerformer
import li.songe.gkd.data.AppRule
import li.songe.gkd.data.ResolvedRule
import li.songe.gkd.data.RuleStatus
import li.songe.gkd.isActivityVisible
import li.songe.gkd.service.A11yService
import li.songe.gkd.priv.privilegeContextFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.showActionToast
import li.songe.gkd.util.systemUiAppId
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds


private val eventDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
private val queryDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
private val actionDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

private const val FAST_EVENT_DELIVERY_TIMEOUT = 0L
private const val PRIVILEGED_IDLE_EVENT_DELIVERY_TIMEOUT = 0L
private const val FALLBACK_IDLE_EVENT_DELIVERY_TIMEOUT = 500L
private const val SPLASH_ACTIVE_WINDOW = 8_000L
private const val POST_TRIGGER_WINDOW = 3_000L

private data class DeliveryProfile(
    val eventTypes: Int,
    val notificationTimeout: Long,
    val cacheEnabled: Boolean,
)

// FAST: 开屏窗口内全量投递, 打开节点缓存以启用预取
private val fastDeliveryProfile = DeliveryProfile(
    eventTypes = STATE_CHANGED or CONTENT_CHANGED,
    notificationTimeout = FAST_EVENT_DELIVERY_TIMEOUT,
    cacheEnabled = true,
)

// 特权空闲: 只留 state 事件, 关闭节点缓存以掐断 cache-critical 强制投递
private val privilegedIdleDeliveryProfile = DeliveryProfile(
    eventTypes = STATE_CHANGED,
    notificationTimeout = PRIVILEGED_IDLE_EVENT_DELIVERY_TIMEOUT,
    cacheEnabled = false,
)

// 非特权空闲: 与历史行为一致, 开窗依赖事件投递
private val fallbackIdleDeliveryProfile = DeliveryProfile(
    eventTypes = STATE_CHANGED or CONTENT_CHANGED,
    notificationTimeout = FALLBACK_IDLE_EVENT_DELIVERY_TIMEOUT,
    cacheEnabled = true,
)

class A11yRuleEngine(val service: A11yCommonImpl) {
    private val a11yContext = A11yContext(this)

    fun onA11yConnected() {
        refreshDeliveryProfile()
        // 特权连接/断开, 匹配开关变化时立即重算档位
        scope.launch(actionDispatcher) {
            privilegeContextFlow.collect { refreshDeliveryProfile() }
        }
        scope.launch(actionDispatcher) {
            storeFlow.map { it.enableMatch }.distinctUntilChanged().collect { refreshDeliveryProfile() }
        }
        startQueryJob(byForced = true)
    }

    fun onScreenForcedActive() {
        // 关闭屏幕 -> Activity::onStop -> 点亮屏幕 -> Activity::onStart -> Activity::onResume
        val a = topActivityFlow.value
        synchronized(topActivityFlow) {
            updateTopActivity(
                a.appId,
                a.activityId,
                scene = ActivityScene.ScreenOn
            )
        }
    }

    fun onTopActivityChanged(idChanged: Boolean) {
        if (idChanged) {
            lastGetAppIdTime = 0L
            lastAppId = null
        }
        a11yContext.interruptKey++
        scope.launch(actionDispatcher) {
            refreshDeliveryProfile()
            startQueryJob()
        }
    }

    val safeActiveWindow: AccessibilityNodeInfo?
        get() = try {
            // 某些应用耗时 554ms
            // java.lang.SecurityException: Call from user 0 as user -2 without permission INTERACT_ACROSS_USERS or INTERACT_ACROSS_USERS_FULL not allowed.
            service.windowNodeInfo?.setGeneratedTime()
        } catch (_: Throwable) {
            null
        }.apply {
            a11yContext.rootCache.value = this
        }

    val safeActiveWindowAppId: String?
        get() = safeActiveWindow?.packageName?.toString()

    private val scope get() = service.scope

    @Volatile
    private var latestStateEvent: A11yEvent? = null
    private var lastContentEventTime = 0L
    private var lastEventTime = 0L
    private val eventDeque = ArrayDeque<A11yEvent>()
    fun onA11yEvent(event: AccessibilityEvent?) {
        if (!event.isUseful()) return
        // 拒绝副屏无障碍事件
        if (event.displayId != Display.DEFAULT_DISPLAY) return
        onA11yFeatEvent(event)
        if (event.eventType == CONTENT_CHANGED) {
            if (!isInteractive) return // 屏幕关闭后仍然有无障碍事件 type:2048, time:8094, app:com.miui.aod, cls:android.widget.TextView
            if (event.packageName == systemUiAppId && event.packageName != topActivityFlow.value.appId) return
            // 当前应用无可活跃规则时, 其内容变化事件无需进入匹配管线
            // 活跃状态由 STATE_CHANGED 驱动, 不受此影响
            if (event.packageName == topActivityFlow.value.appId && activityRuleFlow.value.skipConsumeEvent) return
        }
        // 过滤部分输入法事件
        if (event.packageName == imeAppId && topActivityFlow.value.appId != imeAppId) {
            if (event.recordCount == 0 && event.action == 0 && !event.isFullScreen) return
        }
        // 直接丢弃自身事件，自行更新 topActivity
        if ((event.eventType == CONTENT_CHANGED || !isActivityVisible) && event.packageName == META.appId) return

        val a11yEvent = event.toA11yEvent() ?: return
        if (a11yEvent.type == CONTENT_CHANGED) {
            // 防止同应用空闲期 content 类型事件过快; 跨应用事件可能触发切应用升档, 不节流
            if (
                a11yEvent.appId == topActivityFlow.value.appId &&
                a11yEvent.time - lastContentEventTime < 100 &&
                !windowOrTailActive(a11yEvent.time)
            ) {
                return
            }
            lastContentEventTime = a11yEvent.time
        }
        if (META.debuggable) {
            Log.d(
                "onNewA11yEvent",
                "type:${event.eventType}, time:${event.eventTime - lastEventTime}, app:${event.packageName}, cls:${event.className}"
            )
        }
        if (event.eventTime < lastEventTime) {
            // 某些应用会发送负时间事件, 直接丢弃
            // type:32, time:-104, app:com.miui.home, cls:com.miui.home.launcher.Launcher
            return
        }
        lastEventTime = event.eventTime
        if (event.eventType == STATE_CHANGED) {
            latestStateEvent = a11yEvent
        }
        synchronized(eventDeque) { eventDeque.addLast(a11yEvent) }
        scope.launch(eventDispatcher) { consumeEvent(a11yEvent) }
    }

    private val queryEvents = mutableListOf<A11yEvent>()
    private suspend fun consumeEvent(headEvent: A11yEvent) {
        val consumedEvents = synchronized(eventDeque) {
            if (eventDeque.firstOrNull() !== headEvent) return
            buildList {
                val iterator = eventDeque.iterator()
                while (iterator.hasNext()) {
                    val e = iterator.next()
                    if (e.sameAs(headEvent)) {
                        add(e)
                        iterator.remove()
                    }
                }
            }
        }
        val latestEvent = consumedEvents.last()
        val evAppId = latestEvent.appId
        val evActivityId = latestEvent.name
        val oldAppId = topActivityFlow.value.appId
        val isActivityStateEvent = latestEvent.type == STATE_CHANGED && isActivity(evAppId, evActivityId)
        val rightAppId = if (oldAppId == evAppId) {
            evAppId
        } else {
            getTimeoutAppId(forceRefresh = isActivityStateEvent) ?: return
        }
        if (rightAppId == evAppId) {
            if (isActivityStateEvent) {
                synchronized(topActivityFlow) {
                    // tv.danmaku.bili, com.miui.home, com.miui.home.launcher.Launcher
                    updateTopActivity(evAppId, evActivityId)
                }
            }
        }
        if (rightAppId != topActivityFlow.value.appId) {
            synchronized(topActivityFlow) {
                // 从 锁屏，下拉通知栏 返回等情况, 应用不会发送事件, 但是系统组件会发送事件
                val topCpn = privilegeContextFlow.value?.topCpn()
                if (topCpn?.packageName == rightAppId) {
                    updateTopActivity(
                        topCpn.packageName,
                        topCpn.className,
                        scene = ActivityScene.TaskStack,
                    )
                } else {
                    updateTopActivity(rightAppId, null)
                }
            }
        }
        val activityRule = activityRuleFlow.value
        if (evAppId != rightAppId || activityRule.skipConsumeEvent || !storeFlow.value.enableMatch) {
            return
        }
        // 特权空闲期不做匹配: 事件不进入查询队列, 避免堆积
        if (!gateOpen(System.currentTimeMillis())) {
            return
        }
        synchronized(queryEvents) { queryEvents.addAll(consumedEvents) }
        a11yContext.interruptKey++
        startQueryJob(byEvent = latestEvent)
    }

    @Volatile
    private var lastGetAppIdTime = 0L

    @Volatile
    private var lastAppId: String? = null
    private suspend fun getTimeoutAppId(forceRefresh: Boolean = false): String? {
        val privilegeContext = privilegeContextFlow.value
        if (privilegeContext != null) {
            // 特权通道读取栈顶组件远快于无障碍 root 节点获取, 且不会打开无障碍服务端缓存标志
            // https://github.com/gkd-kit/gkd/issues/622
            return privilegeContext.topCpn()?.packageName.also {
                lastAppId = it
                lastGetAppIdTime = System.currentTimeMillis()
            }
        }
        if (!forceRefresh && lastAppId != null && System.currentTimeMillis() - lastGetAppIdTime <= 100) {
            return lastAppId
        }
        lastAppId = withTimeoutOrNull(100.milliseconds) {
            runInterruptible(Dispatchers.IO) { safeActiveWindowAppId }
        }
        lastGetAppIdTime = System.currentTimeMillis()
        return lastAppId
    }

    private suspend fun getTimeoutActiveWindow(): AccessibilityNodeInfo? {
        return withTimeoutOrNull(500.milliseconds) {
            runInterruptible(Dispatchers.IO) { safeActiveWindow }
        }
    }

    @Volatile
    private var querying = false
    private var pendingQuery = false
    private var pendingForced = false
    private val pendingDelayRules = LinkedHashSet<ResolvedRule>()

    @Synchronized
    private fun startQueryJob(
        byEvent: A11yEvent? = null,
        byForced: Boolean = false,
        byDelayRule: ResolvedRule? = null,
    ) {
        if (!storeFlow.value.enableMatch) return
        // 特权空闲期禁止匹配查询: 查询会打开服务端缓存标志, 令 cache-critical 事件恢复投递
        // 这里不能触发 checkFutureStartJob, 否则会形成 300ms 空转自旋
        if (!gateOpen(System.currentTimeMillis())) return
        if (activityRuleFlow.value.currentRules.isEmpty()) return
        if (querying) {
            if (byDelayRule != null) {
                pendingDelayRules.add(byDelayRule)
            } else if (byEvent == null) {
                pendingQuery = true
                if (byForced) {
                    pendingForced = true
                }
            }
            return
        }
        // 无障碍从零启动时获取 safeActiveWindow 非常耗时
        if (byEvent == null && service.justStarted) return checkFutureStartJob()
        querying = true
        scope.launchTry(queryDispatcher) {
            val st = if (META.debuggable) System.currentTimeMillis() else 0L
            try {
                if (META.debuggable) {
                    Log.d(
                        "A11yRuleEngine",
                        "startQueryJob start byEvent=${byEvent != null}, byForced=$byForced, byDelayRule=${byDelayRule != null}"
                    )
                }
                queryAction(byEvent, byForced, byDelayRule)
            } finally {
                // 查询会把服务端缓存标志重新置位, 标记后在回到空闲档时强制重关
                cacheDirty = true
                applyDeliveryProfile()
                if (META.debuggable) {
                    val et = System.currentTimeMillis() - st
                    Log.d("A11yRuleEngine", "startQueryJob end $et ms")
                }
                onQueryJobFinished()
            }
        }
    }

    @Synchronized
    private fun onQueryJobFinished() {
        querying = false
        val nextDelayRule = pendingDelayRules.firstOrNull()?.also {
            pendingDelayRules.remove(it)
        }
        if (nextDelayRule != null) {
            startQueryJob(byDelayRule = nextDelayRule)
            return
        }
        val nextEvent = synchronized(queryEvents) { queryEvents.lastOrNull() }
        if (nextEvent != null) {
            pendingQuery = false
            pendingForced = false
            startQueryJob(byEvent = nextEvent)
            return
        }
        if (pendingQuery) {
            val forced = pendingForced
            pendingQuery = false
            pendingForced = false
            startQueryJob(byForced = forced)
            return
        }
        checkFutureStartJob()
    }

    private fun checkFutureStartJob() {
        val t = System.currentTimeMillis()
        if (t - lastTriggerTime < 3000L || t - appChangeTime < 3000L) {
            scope.launch(actionDispatcher) {
                delay(300.milliseconds)
                startQueryJob()
            }
        } else if (activityRuleFlow.value.hasFeatureAction) {
            scope.launch(actionDispatcher) {
                delay(300.milliseconds)
                startQueryJob(byForced = true)
            }
        }
    }

    private val deliveryProfileLock = Any()
    private var appliedEventTypes: Int? = null
    private var appliedEventTimeout: Long? = null
    private var appliedCacheEnabled: Boolean? = true
    private var deliveryRestoreJob: Job? = null

    @Volatile
    private var cacheDirty = false

    // 窗口或触发后尾巴是否有效(窗口起点为 appChangeTime: 最近一次 app 切换或亮屏)
    private fun windowOrTailActive(t: Long): Boolean {
        return t - appChangeTime < SPLASH_ACTIVE_WINDOW || t - lastTriggerTime < POST_TRIGGER_WINDOW
    }

    // 门控: 非特权路径保持现状, 特权路径仅窗口/尾巴内允许匹配查询
    private fun gateOpen(t: Long): Boolean {
        return privilegeContextFlow.value == null || windowOrTailActive(t)
    }

    private fun idleDeliveryProfile(): DeliveryProfile {
        return if (privilegeContextFlow.value != null) {
            privilegedIdleDeliveryProfile
        } else {
            fallbackIdleDeliveryProfile
        }
    }

    private fun hasAliveRule(activityRule: ActivityRule): Boolean {
        if (activityRule.blockMatch) return false
        return !activityRule.skipConsumeEvent || activityRule.appRules.any { it.status.alive }
    }

    private fun computeDeliveryProfile(t: Long): DeliveryProfile {
        if (!storeFlow.value.enableMatch) return idleDeliveryProfile()
        if (!windowOrTailActive(t)) return idleDeliveryProfile()
        return if (hasAliveRule(activityRuleFlow.value)) fastDeliveryProfile else idleDeliveryProfile()
    }

    private fun applyDeliveryProfile() {
        val profile = computeDeliveryProfile(System.currentTimeMillis())
        synchronized(deliveryProfileLock) {
            val accessibilityService = service as? AccessibilityService ?: return
            try {
                if (profile.eventTypes != appliedEventTypes || profile.notificationTimeout != appliedEventTimeout) {
                    val info = accessibilityService.serviceInfo ?: return
                    info.eventTypes = profile.eventTypes
                    info.notificationTimeout = profile.notificationTimeout
                    accessibilityService.setServiceInfo(info)
                    appliedEventTypes = profile.eventTypes
                    appliedEventTimeout = profile.notificationTimeout
                }
                if (profile.cacheEnabled) {
                    if (appliedCacheEnabled != true && accessibilityService.setCacheEnabled(true)) {
                        appliedCacheEnabled = true
                    }
                } else if (cacheDirty || appliedCacheEnabled != false) {
                    // 查询会偷偷打开服务端缓存标志, 强制重关路径必须绕过去重
                    if (accessibilityService.setCacheEnabled(false)) {
                        appliedCacheEnabled = false
                        cacheDirty = false
                    }
                }
            } catch (_: Throwable) {
                // 连接异常时不记账, 下次重算时重试
            }
        }
    }

    private fun refreshDeliveryProfile() {
        applyDeliveryProfile()
        deliveryRestoreJob?.cancel()
        if (!windowOrTailActive(System.currentTimeMillis())) return
        deliveryRestoreJob = scope.launch(actionDispatcher) {
            while (true) {
                val remain = maxOf(
                    appChangeTime + SPLASH_ACTIVE_WINDOW,
                    lastTriggerTime + POST_TRIGGER_WINDOW,
                ) - System.currentTimeMillis()
                if (remain > 0) {
                    delay(remain.milliseconds)
                }
                applyDeliveryProfile()
                if (!windowOrTailActive(System.currentTimeMillis())) break
            }
        }
    }

    private fun fixAppId(rightAppId: String) {
        if (topActivityFlow.value.appId == rightAppId) return
        synchronized(topActivityFlow) {
            val topCpn = privilegeContextFlow.value?.topCpn()
            if (topCpn != null) {
                if (!topActivityFlow.value.sameAs(topCpn)) {
                    updateTopActivity(
                        topCpn.packageName,
                        topCpn.className,
                        scene = ActivityScene.TaskStack,
                    )
                }
            } else {
                updateTopActivity(rightAppId, null)
            }
        }
        scope.launch(actionDispatcher) {
            delay(300.milliseconds)
            startQueryJob()
        }
    }

    private suspend fun queryAction(
        byEvent: A11yEvent? = null,
        byForced: Boolean = false,
        delayRule: ResolvedRule? = null,
    ) {
        // 保证第一个节点访问发生前, 档位(含节点缓存与预取)已经生效
        applyDeliveryProfile()
        val tempStateEvent = latestStateEvent
        val newEvents = if (delayRule != null) {// 延迟规则不消耗事件
            null
        } else {
            synchronized(queryEvents) {
                if (byEvent != null && queryEvents.isEmpty()) {
                    return
                }
                (if (queryEvents.size > 1) {
                    val hasDiffItem = queryEvents.any { e ->
                        queryEvents.any { e2 -> !e.sameAs(e2) }
                    }
                    if (hasDiffItem) {
                        // 存在不同的事件节点, 全部丢弃使用 root 查询
                        null
                    } else {
                        // type,appId,className 一致, 需要在 synchronized 外验证是否是同一节点
                        arrayOf(
                            queryEvents[queryEvents.size - 2],
                            queryEvents.last(),
                        )
                    }
                } else if (queryEvents.size == 1) {
                    arrayOf(queryEvents.last())
                } else {
                    null
                }).apply {
                    queryEvents.clear()
                }
            }
        }
        val activityRule = synchronized(topActivityFlow) { activityRuleFlow.value }
        activityRule.currentRules.forEach { rule ->
            if (rule.status == RuleStatus.Status3 && rule.matchDelayJob.value == null) {
                rule.matchDelayJob.value = scope.launch(actionDispatcher) {
                    delay(rule.matchDelay.milliseconds)
                    rule.matchDelayJob.value = null
                    startQueryJob(byDelayRule = rule)
                }
            }
        }
        if (activityRule.skipMatch) {
            // 如果当前应用没有规则/暂停匹配, 则不去调用获取事件节点避免阻塞
            return
        }
        var lastNode = if (newEvents == null || newEvents.size <= 1) {
            newEvents?.firstOrNull()?.safeSource
        } else {
            // 获取最后两个事件, 如果最后两个事件的节点不一致, 则丢弃
            // 相等则是同一个节点发出的连续事件, 常见于倒计时界面
            val lastNode = newEvents.last().safeSource
            if (lastNode == null || lastNode == newEvents[0].safeSource) {
                lastNode
            } else {
                null
            }
        }
        var lastNodeUsed = false
        if (!a11yContext.clearOldAppNodeCache()) {
            // 新事件或轮询到来时, 若缓存清理不及时会导致无法查询到节点
            a11yContext.clearNodeCache(lastNode)
        }
        for (rule in activityRule.priorityRules) { // 规则数量有可能过多导致耗时过长
            if (checkOutDate(activityRule, tempStateEvent)) break
            if (delayRule != null && delayRule !== rule) continue
            if (rule.status != RuleStatus.StatusOk) continue
            if (byForced && !rule.checkForced()) continue
            lastNode?.let { n ->
                val refreshOk = (!lastNodeUsed) || (try {
                    val e = n.refresh()
                    if (e) {
                        n.setGeneratedTime()
                    }
                    e
                } catch (_: Throwable) {
                    false
                })
                lastNodeUsed = true
                if (!refreshOk) {
                    lastNode = null
                }
            }
            val nodeVal = (lastNode ?: getTimeoutActiveWindow()) ?: continue
            val rightAppId = nodeVal.packageName?.toString() ?: break
            val matchApp = rule.matchActivity(rightAppId)
            if (topActivityFlow.value.appId != rightAppId || (!matchApp && rule is AppRule)) {
                a11yContext.clearNodeCache()
                scope.launch(eventDispatcher) { fixAppId(rightAppId) }
                return
            }
            if (!matchApp) continue
            val target = a11yContext.queryRule(rule, nodeVal) ?: continue
            if (rule.checkDelay() && rule.actionDelayJob.value == null) {
                rule.actionDelayJob.value = scope.launch(actionDispatcher) {
                    delay(rule.actionDelay.milliseconds)
                    rule.actionDelayJob.value = null
                    startQueryJob(byDelayRule = rule)
                }
                continue
            }
            if (rule.status != RuleStatus.StatusOk) break
            if (checkOutDate(activityRule, tempStateEvent)) break
            val actionResult = rule.performAction(target)
            if (actionResult.result) {
                val topActivity = topActivityFlow.value
                rule.trigger()
                // 触发后的 3 秒尾巴参与关窗时刻, 需要重排降档
                refreshDeliveryProfile()
                scope.launch(actionDispatcher) {
                    delay(300.milliseconds)
                    startQueryJob()
                }
                if (actionResult.action != ActionPerformer.None.action) {
                    showActionToast(rule)
                }
                addActionLog(rule, topActivity, target, actionResult)
            }
        }
    }

    private fun checkOutDate(
        activityRule: ActivityRule,
        stateEvent: A11yEvent?
    ): Boolean {
        if (stateEvent !== latestStateEvent) return true
        synchronized(topActivityFlow) {
            if (activityRule !== activityRuleFlow.value) return true
        }
        return false
    }

    companion object {
        val service: A11yCommonImpl?
            get() = A11yService.instance
        val instance: A11yRuleEngine? get() = service?.ruleEngine

        fun onScreenForcedActive() {
            instance?.onScreenForcedActive()
        }
    }
}
