package li.songe.gkd.a11y

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope

interface A11yCommonImpl {
    val windowNodeInfo: AccessibilityNodeInfo?
    val scope: CoroutineScope
    var justStarted: Boolean
    val ruleEngine: A11yRuleEngine
    fun shutdown()
}
