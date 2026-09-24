package li.songe.gkd.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import li.songe.gkd.MainViewModel
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.edit
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.UpsertRuleGroupRoute
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.SubscriptionStore
import li.songe.gkd.util.getGroupEnable
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

data class ShowGroupState(
    val subsId: Long,
    val appId: String? = null,
    val groupKey: Int? = null,
    val pageAppId: String? = null,
) {
    val groupType: Int
        get() = SubsConfig.AppGroupType

    suspend fun querySubsConfig(): SubsConfig? = querySubsConfigFlow().first()
}

private fun ShowGroupState.querySubsConfigFlow(): Flow<SubsConfig?> {
    val groupKey = groupKey ?: error("require groupKey")
    val appId = appId ?: error("require appId")
    return DbSet.subsConfigDao.queryAppGroupTypeConfig(subsId, appId, groupKey)
}

private data class ExcludeEditSession(
    val groupState: ShowGroupState,
    val originalConfig: SubsConfig?,
)

fun RawSubscription.RawGroupProps.toGroupState(
    subsId: Long,
    appId: String? = null,
) = when (this) {
    is RawSubscription.RawAppGroup -> ShowGroupState(
        subsId = subsId,
        appId = appId ?: error("require appId"),
        groupKey = key,
        pageAppId = appId,
    )

    is RawSubscription.RawGlobalGroup -> error("unsupported global group")
}

suspend fun updateRuleGroupEnable(
    subscription: RawSubscription,
    appId: String,
    group: RawSubscription.RawGroupProps,
    subsConfig: SubsConfig?,
    enabled: Boolean,
) {
    val newConfig = (subsConfig?.copy(enable = enabled) ?: SubsConfig(
        type = SubsConfig.AppGroupType,
        subsId = subscription.id,
        appId = appId,
        groupKey = group.key,
        enable = enabled,
    ))
    DbSet.subsConfigDao.insert(newConfig)
}

suspend fun batchUpdateGroupEnable(
    groups: Collection<ShowGroupState>,
    enable: Boolean?
): List<Pair<ShowGroupState, SubsConfig>> {
    val diffDataList = groups.map { g ->
        if (g.groupKey == null) return@map null
        val subscription = runCatching {
            SubscriptionStore.awaitSubscription(g.subsId)
        }.getOrNull() ?: return@map null
        val targetGroup = if (g.appId != null) {
            subscription.apps.find { a -> a.id == g.appId }?.groups?.find { it.key == g.groupKey }
        } else {
            return@map null
        }
        if (targetGroup?.valid != true) {
            return@map null
        }
        val subsConfig = g.querySubsConfig()
        if (enable == null && subsConfig?.enable == null && subsConfig?.exclude.isNullOrEmpty()) {
            return@map null
        }
        val oldEnable = getGroupEnable(targetGroup, subsConfig)
        // app rule
        val newSubsConfig = (subsConfig?.copy(enable = enable) ?: SubsConfig(
            type = SubsConfig.AppGroupType,
            subsId = g.subsId,
            appId = g.appId,
            groupKey = g.groupKey,
            enable = enable
        ))
        val newEnable = getGroupEnable(targetGroup, newSubsConfig)
        if (enable == newEnable && oldEnable == newEnable) {
            return@map null
        }

        if (subsConfig != newSubsConfig) {
            g to newSubsConfig
        } else {
            null
        }
    }.filterNotNull()
    val newSubsConfigs = diffDataList.map { it.second }
    val canDeleteList = newSubsConfigs.filter {
        it.type == SubsConfig.AppGroupType && it.enable == null && it.exclude.isEmpty()
    }
    DbSet.subsConfigDao.insertAndDelete(
        newSubsConfigs.filterNot { canDeleteList.contains(it) },
        canDeleteList
    )
    return diffDataList
}

class RuleGroupState(
    private val mainVm: MainViewModel,
) {
    private val showGroupFlow = MutableStateFlow<ShowGroupState?>(null)
    private val dismissGroupShow = { showGroupFlow.value = null }

    fun showGroup(state: ShowGroupState) {
        showGroupFlow.value = state
    }

    private val excludeEditSessionFlow = MutableStateFlow<ExcludeEditSession?>(null)
    private val excludeTextFlow = MutableStateFlow("")
    private val dismissExcludeGroupShow = {
        excludeEditSessionFlow.value = null
        excludeTextFlow.value = ""
    }
    private fun getChangedExcludeData(session: ExcludeEditSession): ExcludeData? {
        val oldValue = ExcludeData.parse(session.originalConfig?.exclude)
        val newValue = ExcludeData.parse(
            excludeTextFlow.value,
            requireNotNull(session.groupState.appId),
        )
        return newValue.takeIf { it != oldValue }
    }

    private fun openExcludeEditor(state: ShowGroupState) {
        dismissGroupShow()
        val appId = requireNotNull(state.appId)
        mainVm.scope.launchTry {
            val originalConfig = state.querySubsConfig()
            excludeTextFlow.value = ExcludeData.parse(originalConfig?.exclude)
                .stringify(appId)
            excludeEditSessionFlow.value = ExcludeEditSession(state, originalConfig)
        }
    }

    private suspend fun resetGroupSwitch(
        state: ShowGroupState,
        group: RawSubscription.RawGroupProps,
        subsConfig: SubsConfig,
    ): String {
        DbSet.subsConfigDao.update(subsConfig.copy(enable = null))
        return "已重置开关至默认值"
    }

    private suspend fun deleteGroup(
        state: ShowGroupState,
    ) {
        val groupKey = requireNotNull(state.groupKey)
        val appId = requireNotNull(state.appId)
        SubscriptionStore.update(state.subsId) { subscription ->
            subscription.edit {
                if (subscription.apps.none { it.id == appId }) {
                    error("应用规则已不存在")
                }
                if (removeAppGroups(appId) { it.key == groupKey }.isEmpty()) {
                    error("规则已不存在")
                }
            }
        }
    }

    private suspend fun saveChangedExclude(
        session: ExcludeEditSession,
        subscription: RawSubscription,
        excludeData: ExcludeData,
    ) {
        val state = session.groupState
        val appId = requireNotNull(state.appId)
        val groupKey = requireNotNull(state.groupKey)
        val newSubsConfig = (session.originalConfig ?: SubsConfig(
            type = SubsConfig.AppGroupType,
            subsId = subscription.id,
            appId = appId,
            groupKey = groupKey,
        )).copy(exclude = excludeData.stringify())
        DbSet.subsConfigDao.insert(newSubsConfig)
    }

    @Composable
    fun Render() {
        val showGroupState = showGroupFlow.collectAsStateWithLifecycle().value
        val showSubs = useSubs(showGroupState?.subsId)
        val showGroup = useSubsGroup(showSubs, showGroupState?.groupKey, showGroupState?.appId)
        if (showGroupState?.groupKey != null && showSubs != null && showGroup != null) {
            val subsConfigFlow = remember(showGroupState) {
                showGroupState.querySubsConfigFlow()
            }
            val subsConfig = subsConfigFlow.collectAsStateWithLifecycle(null).value
            RuleGroupDialog(
                subs = showSubs,
                group = showGroup,
                appId = showGroupState.appId,
                onDismissRequest = dismissGroupShow,
                onClickEdit = {
                    dismissGroupShow()
                    mainVm.navigatePage(
                        UpsertRuleGroupRoute(
                            subsId = showGroupState.subsId,
                            groupKey = showGroupState.groupKey,
                            appId = showGroupState.appId,
                        )
                    )
                },
                onClickEditExclude = {
                    openExcludeEditor(showGroupState)
                },
                onClickResetSwitch = subsConfig?.let {
                    subsConfig.enable?.let {
                        mainVm.scope.launchAsFn {
                            toast(resetGroupSwitch(showGroupState, showGroup, subsConfig))
                        }
                    }
                },
                onClickDelete = mainVm.scope.launchAsFn {
                    val r = mainVm.dialogRequests.confirm(
                        title = "删除规则",
                        text = "确定删除 ${showGroup.name} ?",
                        error = true,
                    )
                    if (!r) {
                        return@launchAsFn
                    }
                    deleteGroup(showGroupState)
                    dismissGroupShow()
                    toast("删除成功")
                }
            )
        }

        val excludeEditSession = excludeEditSessionFlow.collectAsStateWithLifecycle().value
        val excludeGroupState = excludeEditSession?.groupState
        val excludeSubs = useSubs(excludeGroupState?.subsId)
        val excludeGroup =
            useSubsGroup(excludeSubs, excludeGroupState?.groupKey, excludeGroupState?.appId)
        if (excludeEditSession != null && excludeGroupState?.groupKey != null && excludeGroupState.appId != null && excludeSubs != null && excludeGroup is RawSubscription.RawAppGroup) {
            FullscreenDialog(onDismissRequest = dismissExcludeGroupShow) {
                val keyboardController = LocalSoftwareKeyboardController.current
                val onBack = mainVm.scope.launchAsFn {
                    keyboardController?.hide()
                    val newValue = getChangedExcludeData(excludeEditSession)
                    if (newValue != null) {
                        if (!mainVm.dialogRequests.confirm(
                            title = "提示",
                            text = "当前内容未保存，是否放弃编辑？",
                        )) return@launchAsFn
                    }
                    dismissExcludeGroupShow()
                }
                BackHandler(onBack = onBack)
                Scaffold(
                    topBar = {
                        PerfTopAppBar(
                            navigationIcon = {
                                PerfIconButton(
                                    imageVector = PerfIcon.Close,
                                    onClick = onBack
                                )
                            },
                            title = {
                                TowLineText(
                                    title = excludeGroup.name,
                                    subtitle = "编辑禁用",
                                )
                            },
                            actions = {
                                PerfIconButton(imageVector = PerfIcon.Save, onClick = throttle {
                                    val newValue = getChangedExcludeData(excludeEditSession)
                                    if (newValue == null) {
                                        toast("无修改")
                                        dismissExcludeGroupShow()
                                    } else {
                                        dismissExcludeGroupShow()
                                        mainVm.scope.launchTry {
                                            saveChangedExclude(
                                                excludeEditSession,
                                                excludeSubs,
                                                newValue,
                                            )
                                            toast("更新成功")
                                        }
                                    }
                                })
                            }
                        )
                    },
                ) { contentPadding ->
                    MultiTextField(
                        modifier = Modifier.scaffoldPadding(contentPadding),
                        textFlow = excludeTextFlow,
                        placeholderText = "请填入需要禁用的 activityId 列表\n每行一个",
                    )
                }
            }
        }
    }
}
