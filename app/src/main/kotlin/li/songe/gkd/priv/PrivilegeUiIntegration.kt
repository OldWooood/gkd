package li.songe.gkd.priv

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.appScope
import li.songe.gkd.store.createTextFlow
import priv.kit.core.Privilege

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
    val serverState = Privilege.serverState
    val resolve = { connected: Boolean, desired: Boolean ->
        when {
            connected -> PrivilegeServiceStatus.Connected
            desired -> PrivilegeServiceStatus.DisconnectedDesired
            else -> PrivilegeServiceStatus.Disconnected
        }
    }
    combine(serverState, privilegeDesiredEnabledFlow) { serverInfo, desired ->
        resolve(serverInfo != null, desired)
    }.stateIn(
        appScope,
        SharingStarted.Eagerly,
        resolve(serverState.value != null, privilegeDesiredEnabledFlow.value),
    )
}
