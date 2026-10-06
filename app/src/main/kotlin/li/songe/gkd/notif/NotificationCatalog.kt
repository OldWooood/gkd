package li.songe.gkd.notif

import android.app.Service
import li.songe.gkd.META
import li.songe.gkd.R
import kotlin.reflect.KClass

enum class ForegroundNotificationKey(
    val id: Int,
    val channel: AppNotificationChannel = AppNotificationChannel.Service,
) {
    Status(id = 100),
}

sealed interface AppNotificationSpec {
    val id: Int
    val channel: AppNotificationChannel
    val smallIcon: Int
    val title: String
    val text: String?
    val uri: String?
    val ongoing: Boolean
    val autoCancel: Boolean
    val stopService: KClass<out Service>?
}

data class ForegroundNotification(
    val key: ForegroundNotificationKey,
    override val title: String,
    override val text: String? = null,
    override val uri: String? = null,
    override val smallIcon: Int = R.drawable.ic_status,
    override val stopService: KClass<out Service>? = null,
) : AppNotificationSpec {
    override val id: Int
        get() = key.id
    override val channel: AppNotificationChannel
        get() = key.channel
    override val ongoing = true
    override val autoCancel = false

    context(service: Service)
    fun startForeground() = NotificationDispatcher.startForeground(service, this)
}

object NotificationCatalog {
    fun status(
        title: String = META.appName,
        text: String? = "无障碍正在运行",
        uri: String? = null,
    ) = ForegroundNotification(
        key = ForegroundNotificationKey.Status,
        title = title,
        text = text,
        uri = uri,
    )
}
