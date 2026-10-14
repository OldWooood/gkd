package li.songe.gkd.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.actionCountFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.Loadable
import li.songe.gkd.util.SubscriptionResult
import li.songe.gkd.util.SubscriptionSnapshot
import li.songe.gkd.util.SubscriptionStore
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.toast

data class HomeSubsUiState(
    val subItems: List<SubsItem>,
    val subscriptions: Map<Long, RawSubscription>,
    val refreshing: Boolean,
    val loadErrors: Map<Long, Exception>,
    val refreshErrors: Map<Long, Exception>,
)

private fun buildHomeSubsUiState(
    subItems: List<SubsItem>,
    snapshot: SubscriptionSnapshot,
    refreshing: Boolean,
) = HomeSubsUiState(
    subItems = subItems,
    subscriptions = snapshot.subscriptions,
    refreshing = refreshing,
    loadErrors = snapshot.loadErrors,
    refreshErrors = snapshot.updateErrors,
)

class HomeVm : BaseViewModel() {

    val subsStatusFlow = combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
        getSubsStatus(ruleSummary, count)
    }.stateInit(getSubsStatus(ruleSummaryFlow.value, actionCountFlow.value))

    val settingsDialogVisibleFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val powerWarningItemFlow: StateFlow<SubsItem?>
        field = MutableStateFlow(null)
    val showActionToastDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val uiState: StateFlow<Loadable<HomeSubsUiState>> =
        SubscriptionStore.snapshotFlow.flatMapLatest { snapshotState ->
            when (snapshotState) {
                Loadable.Loading -> flowOf(Loadable.Loading)
                is Loadable.Failure -> flowOf(snapshotState)
                is Loadable.Ready -> combine(
                    DbSet.subsItemDao.query(),
                    SubscriptionStore.updating,
                ) { subItems, refreshing ->
                    buildHomeSubsUiState(
                        subItems = subItems,
                        snapshot = snapshotState.value,
                        refreshing = refreshing,
                    )
                }.map<HomeSubsUiState, Loadable<HomeSubsUiState>> { Loadable.Ready(it) }
                    .catch { emit(Loadable.Failure(it)) }
            }
        }.stateIn(scope, SharingStarted.Eagerly, Loadable.Loading)

    fun setUpdateInterval(value: Long) {
        storeFlow.update { it.copy(updateSubsInterval = value) }
    }

    fun setPowerWarningEnabled(enabled: Boolean) {
        storeFlow.update { it.copy(subsPowerWarn = enabled) }
    }

    fun setSettingsDialogVisible(visible: Boolean) {
        settingsDialogVisibleFlow.value = visible
    }

    fun setEnableMatch(enabled: Boolean) {
        storeFlow.update { it.copy(enableMatch = enabled) }
    }

    fun refresh() {
        scope.launchTry(Dispatchers.IO) {
            SubscriptionStore.refresh().message?.let { toast(it) }
        }
    }

    suspend fun deleteSubscriptions(ids: Set<Long>): SubscriptionResult =
        SubscriptionStore.delete(*ids.toLongArray())

    private fun shouldWarnBeforeEnabling(item: SubsItem): Boolean {
        val state = uiState.value.value ?: return false
        return storeFlow.value.subsPowerWarn &&
            state.subItems.any { current ->
                current.id != item.id &&
                    current.enable &&
                    state.subscriptions[current.id]?.hasRule != false
            }
    }

    fun requestSubscriptionEnabled(item: SubsItem, enabled: Boolean) {
        if (enabled && shouldWarnBeforeEnabling(item)) {
            powerWarningItemFlow.value = item
        } else {
            setSubscriptionEnabled(item, enabled)
        }
    }

    fun dismissPowerWarning() {
        powerWarningItemFlow.value = null
    }

    fun confirmPowerWarning() {
        val item = powerWarningItemFlow.value ?: return
        powerWarningItemFlow.value = null
        setSubscriptionEnabled(item, true)
    }

    private fun setSubscriptionEnabled(item: SubsItem, enabled: Boolean) {
        scope.launchTry(Dispatchers.IO) {
            DbSet.subsItemDao.updateEnable(item.id, enabled)
        }
    }

    suspend fun addOrModifySubscription(
        url: String,
        oldItem: SubsItem? = null,
    ): SubscriptionResult = SubscriptionStore.addOrModifyRemote(url, oldItem)

    fun setActionToastDialogVisible(visible: Boolean) {
        showActionToastDialogFlow.value = visible
    }

    fun saveActionToast(value: String): Boolean {
        if (value == storeFlow.value.actionToast) return false
        storeFlow.update { it.copy(actionToast = value) }
        return true
    }

    fun setToastWhenClick(enabled: Boolean) {
        storeFlow.update { it.copy(toastWhenClick = enabled) }
    }

    fun setExcludeFromRecents(enabled: Boolean) {
        storeFlow.update { it.copy(excludeFromRecents = enabled) }
    }
}
