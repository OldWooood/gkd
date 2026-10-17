package li.songe.gkd.priv

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.appScope
import li.songe.gkd.store.createTextFlow

enum class PrivilegeServiceStatus {
    Connected,
    Disconnected,
    DisconnectedDesired,
}

val privilegeDesiredEnabledFlow = createTextFlow(
    key = "privilege_desired_enabled",
    decode = { it == "true" },
    encode = { it.toString() },
)

val privilegeServiceStatusFlow by lazy {
    val resolve = { connected: Boolean, desired: Boolean ->
        when {
            connected -> PrivilegeServiceStatus.Connected
            desired -> PrivilegeServiceStatus.DisconnectedDesired
            else -> PrivilegeServiceStatus.Disconnected
        }
    }
    combine(privilegeContextFlow, privilegeDesiredEnabledFlow) { context, desired ->
        resolve(context != null, desired)
    }.stateIn(
        appScope,
        SharingStarted.Eagerly,
        resolve(privilegeContextFlow.value != null, privilegeDesiredEnabledFlow.value),
    )
}
