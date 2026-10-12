package li.songe.gkd.notif

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import li.songe.gkd.META
import kotlin.reflect.KClass

class StopServiceReceiver(private val service: Service) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        if (intent.action == STOP_ACTION && intent.getStringExtra(STOP_ACTION) == service.javaClass.name) {
            service.stopSelf()
        }
    }

    companion object {
        private val STOP_ACTION by lazy { META.appId + ".STOP_SERVICE" }

        fun getIntent(clazz: KClass<out Service>) = Intent().apply {
            action = STOP_ACTION
            putExtra(STOP_ACTION, clazz.java.name)
            setPackage(META.appId)
        }
    }
}
