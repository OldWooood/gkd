package li.songe.gkd.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import li.songe.gkd.appScope
import li.songe.gkd.util.AppListString
import li.songe.gkd.util.launchTry

val storeFlow by lazy {
    createAnyFlow(
        key = "store",
        default = { SettingsStore() }
    )
}

val actionCountFlow by lazy {
    createTextFlow(
        key = "action_count",
        decode = { it?.toLongOrNull() ?: 0L },
        encode = { it.toString() },
    )
}

val blockMatchAppListFlow by lazy {
    createTextFlow(
        key = "block_match_app_list",
        decode = { it?.let(AppListString::decode) ?: AppListString.getDefaultBlockList() },
        encode = AppListString::encode,
    )
}

fun checkAppBlockMatch(appId: String): Boolean {
    return blockMatchAppListFlow.value.contains(appId)
}

fun initStore() = appScope.launchTry(Dispatchers.IO) {
    // preload
    storeFlow.value
    actionCountFlow.value
    blockMatchAppListFlow.value
}

fun updateEnableAutomator(value: Boolean) {
    if (value == storeFlow.value.enableAutomator) return
    storeFlow.update { it.copy(enableAutomator = value) }
}
