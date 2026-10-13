package li.songe.gkd.data

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import li.songe.gkd.a11y.A11yRuleEngine
import li.songe.gkd.priv.privilegeContextFlow
import li.songe.gkd.priv.toHidden
import li.songe.gkd.service.A11yService
import li.songe.gkd.util.ScreenUtils
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class ActionResult(
    val action: String,
    val result: Boolean,
    val shell: Boolean = false,
    val position: Pair<Float, Float>? = null,
)

sealed class ActionPerformer(val action: String) {
    abstract suspend fun perform(
        node: AccessibilityNodeInfo,
        locationProps: RawSubscription.LocationProps,
    ): ActionResult

    data object ClickNode : ActionPerformer("clickNode") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            )
        }
    }

    data object ClickCenter : ActionPerformer("clickCenter") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            val rect = node.toHidden.boundsInScreen
            val p = locationProps.position?.calc(rect)
            val x = p?.first ?: ((rect.right + rect.left) / 2f)
            val y = p?.second ?: ((rect.bottom + rect.top) / 2f)
            if (!ScreenUtils.inScreen(x, y)) {
                return ActionResult(
                    action = action,
                    result = false,
                    position = x to y,
                )
            }
            return ActionResult(
                action = action,
                result = if (
                    privilegeContextFlow.value?.tap(x, y) == true
                ) {
                    true
                } else {
                    val gestureDescription = GestureDescription.Builder()
                    val path = Path()
                    path.moveTo(x, y)
                    gestureDescription.addStroke(
                        GestureDescription.StrokeDescription(
                            path, 0, ViewConfiguration.getTapTimeout().toLong()
                        )
                    )
                    A11yService.instance?.dispatchGesture(
                        gestureDescription.build(), null, null
                    ) != null
                },
                position = x to y
            )
        }
    }

    data object Click : ActionPerformer("click") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            if (node.isClickable) {
                val result = ClickNode.perform(node, locationProps)
                if (result.result) {
                    return result
                }
            }
            return ClickCenter.perform(node, locationProps)
        }
    }

    data object None : ActionPerformer("none") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = true
            )
        }
    }

    companion object {
        private val allSubObjects by lazy {
            arrayOf(
                ClickNode,
                ClickCenter,
                Click,
                None,
            )
        }

        fun getAction(action: String?): ActionPerformer {
            return allSubObjects.find { it.action == action } ?: Click
        }
    }
}
