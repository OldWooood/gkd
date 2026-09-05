package li.songe.gkd.service

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.songe.gkd.META
import li.songe.gkd.appScope
import li.songe.gkd.data.toA11yEventLog
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.launchTry
import kotlin.time.Duration.Companion.milliseconds

// 无悬浮窗的事件日志落盘器：替代原 EventService 的 DB 部分
// 调用方为无障碍 Binder 线程，只做无锁入队，批量写库在 appScope 完成
object EventLogStore {
    private var logAutoId = 0

    private val pendingFlow = MutableStateFlow(emptyList<li.songe.gkd.data.A11yEventLog>())

    init {
        appScope.launch {
            logAutoId = (DbSet.a11yEventLogDao.maxId() ?: 0).coerceAtLeast(1)
        }
        appScope.launch {
            while (isActive) {
                delay(1000.milliseconds)
                val list = pendingFlow.getAndUpdate { emptyList() }
                if (list.isNotEmpty()) {
                    DbSet.a11yEventLogDao.insert(list)
                }
            }
        }
    }

    fun logEvent(event: AccessibilityEvent) {
        if (event.packageName == META.appId) return
        if (logAutoId == 0) return
        logAutoId++
        val eventLog = event.toA11yEventLog(logAutoId)
        pendingFlow.update { it + eventLog }
        if (eventLog.id % 100 == 0) {
            appScope.launchTry { DbSet.a11yEventLogDao.deleteKeepLatest() }
        }
    }
}
