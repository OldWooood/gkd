package li.songe.gkd.priv

import android.app.ActivityManager
import android.app.IActivityManager
import android.content.Context

class CompatActivityManager {
    val value: IActivityManager = IActivityManager.Stub.asInterface(
        shizukuSystemService(Context.ACTIVITY_SERVICE),
    )

    fun getTasks(maxNum: Int = 1): List<ActivityManager.RunningTaskInfo> = value.getTasks(maxNum)
}
