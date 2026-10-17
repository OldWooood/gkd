@file:Suppress("CAST_NEVER_SUCCEEDS")

package li.songe.gkd.priv

import android.app.ActivityManager
import android.app.TaskInfoHidden
import android.content.pm.PackageInfo
import android.content.pm.PackageInfoHidden
import android.view.MotionEvent
import android.view.MotionEventHidden
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfoHidden

inline val AccessibilityNodeInfo.toHidden get() = this as AccessibilityNodeInfoHidden

inline val PackageInfo.toHidden get() = this as PackageInfoHidden

inline val ActivityManager.RunningTaskInfo.toHidden get() = this as TaskInfoHidden

inline val MotionEvent.toHidden get() = this as MotionEventHidden
