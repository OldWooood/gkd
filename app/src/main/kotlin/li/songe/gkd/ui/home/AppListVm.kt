package li.songe.gkd.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainViewModel
import li.songe.gkd.data.AppInfo
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.useAppFilter
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.RuleSummary
import li.songe.gkd.util.appListAuthAbnormalFlow
import li.songe.gkd.util.findOption
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.updateAllAppInfo
import li.songe.gkd.util.updateAppMutex

data class AppListUiState(
    val appInfos: List<AppInfo>,
    val searchText: String,
    val showSearchBar: Boolean,
    val showAllApps: Boolean,
    val ruleSummary: RuleSummary,
    val canQueryPackages: Boolean,
    val queryPackagesAbnormal: Boolean,
    val refreshing: Boolean,
)

private data class AppListContentState(
    val appInfos: List<AppInfo>,
    val searchText: String,
    val showAllApps: Boolean,
    val ruleSummary: RuleSummary,
)

private data class AppListEnvironment(
    val canQueryPackages: Boolean,
    val queryPackagesAbnormal: Boolean,
    val refreshing: Boolean,
)

private fun buildAppListUiState(
    showSearchBar: Boolean,
    content: AppListContentState,
    environment: AppListEnvironment,
) = AppListUiState(
    appInfos = content.appInfos,
    searchText = content.searchText,
    showSearchBar = showSearchBar,
    showAllApps = content.showAllApps,
    ruleSummary = content.ruleSummary,
    canQueryPackages = environment.canQueryPackages,
    queryPackagesAbnormal = environment.queryPackagesAbnormal,
    refreshing = environment.refreshing,
)

class AppListVm(mainVm: MainViewModel) : BaseViewModel() {
    private val appFilter = useAppFilter(
        mainVm = mainVm,
        appGroupType = { it.appGroupType },
        sortType = { AppSortOption.objects.findOption(it.appSort) },
        showBlockApps = { it.showBlockApp },
    )
    private val showSearchBarFlow = MutableStateFlow(false)

    val appInfosFlow = appFilter.appListFlow

    private val contentState = combine(
        appFilter.appListFlow,
        appFilter.searchStrFlow,
        appFilter.showAllAppFlow,
        ruleSummaryFlow,
    ) { appInfos, searchText, showAllApps, ruleSummary ->
        AppListContentState(
            appInfos = appInfos,
            searchText = searchText,
            showAllApps = showAllApps,
            ruleSummary = ruleSummary,
        )
    }
    private val environment = combine(
        PermissionStates.queryPackages.stateFlow,
        appListAuthAbnormalFlow,
        updateAppMutex.state,
    ) { canQueryPackages, queryPackagesAbnormal, refreshing ->
        AppListEnvironment(
            canQueryPackages = canQueryPackages,
            queryPackagesAbnormal = queryPackagesAbnormal,
            refreshing = refreshing,
        )
    }

    val uiState = combine(showSearchBarFlow, contentState, environment) { showSearchBar, content, environment ->
        buildAppListUiState(showSearchBar, content, environment)
    }.stateInit(
        buildAppListUiState(
            showSearchBar = showSearchBarFlow.value,
            content = AppListContentState(
                appInfos = appFilter.appListFlow.value,
                searchText = appFilter.searchStrFlow.value,
                showAllApps = appFilter.showAllAppFlow.value,
                ruleSummary = ruleSummaryFlow.value,
            ),
            environment = AppListEnvironment(
                canQueryPackages = PermissionStates.queryPackages.stateFlow.value,
                queryPackagesAbnormal = appListAuthAbnormalFlow.value,
                refreshing = updateAppMutex.state.value,
            ),
        ),
    )

    fun setSearchText(value: String) {
        appFilter.searchStrFlow.value = value.trim()
    }

    fun closeSearch() {
        appFilter.searchStrFlow.value = ""
        showSearchBarFlow.value = false
    }

    fun toggleSearch() {
        if (showSearchBarFlow.value) {
            if (appFilter.searchStrFlow.value.isEmpty()) {
                showSearchBarFlow.value = false
            } else {
                appFilter.searchStrFlow.value = ""
            }
        } else {
            showSearchBarFlow.value = true
        }
    }

    fun onLeaveScreen() {
        if (appFilter.searchStrFlow.value.isEmpty()) {
            showSearchBarFlow.value = false
        }
    }

    fun setSortType(value: AppSortOption) {
        storeFlow.update { it.copy(appSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        storeFlow.update { it.copy(appGroupType = value) }
    }

    fun setShowBlockApp(value: Boolean) {
        storeFlow.update { it.copy(showBlockApp = value) }
    }

    fun refresh() {
        updateAllAppInfo()
    }
}
