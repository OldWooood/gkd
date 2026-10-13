package li.songe.gkd.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import li.songe.gkd.app

fun Context.tryStartActivity(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        LogUtils.d("tryStartActivity", e)
        toast("跳转失败\n" + (e.message ?: e.stackTraceToString()))
    }
}

fun openWeChatScaner() {
    val intent = app.packageManager.getLaunchIntentForPackage("com.tencent.mm")?.apply {
        putExtra("LauncherUI.From.Scaner.Shortcut", true)
    }
    if (intent == null) {
        toast("请检查微信是否安装或禁用")
        return
    }
    app.tryStartActivity(intent)
}

fun openA11ySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    app.tryStartActivity(intent)
}

fun openAppDetailsSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:${app.packageName}".toUri()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    app.tryStartActivity(intent)
}

fun openUri(uri: String) {
    val u = try {
        uri.toUri()
    } catch (e: Exception) {
        e.printStackTrace()
        toast("非法链接")
        return
    }
    openUri(u)
}

fun openUri(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    app.tryStartActivity(intent)
}

