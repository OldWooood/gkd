package li.songe.gkd.util

import android.annotation.SuppressLint
import android.content.res.Resources

@SuppressLint("DiscouragedApi", "InternalInsetResource")
object BarUtils {
    fun getStatusBarHeight(): Int {
        val resources = Resources.getSystem()
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return resources.getDimensionPixelSize(resourceId)
    }
}
