package li.songe.gkd.priv

import android.app.ActivityManager
import android.app.IActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import priv.kit.core.binder.PrivilegeBinderWrapper

class CompatActivityManager {
    val value: IActivityManager = IActivityManager.Stub.asInterface(
        requireNotNull(
            PrivilegeBinderWrapper.fromSystemService(Context.ACTIVITY_SERVICE),
        ),
    )

    fun getTasks(maxNum: Int = 1): List<ActivityManager.RunningTaskInfo> = value.getTasks(maxNum)

    fun startService(
        intent: Intent,
        requireForeground: Boolean,
        callingPackage: String,
        callingFeatureId: String?,
        userId: Int = currentUserId,
    ): ComponentName = value.startService(
        null,
        intent,
        intent.type,
        requireForeground,
        callingPackage,
        callingFeatureId,
        userId,
    )
}
