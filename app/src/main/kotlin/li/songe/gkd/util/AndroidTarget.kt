package li.songe.gkd.util

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

object AndroidTarget {
    /** Android 17+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.CINNAMON_BUN)
    val CINNAMON_BUN = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
}
