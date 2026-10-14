package li.songe.gkd.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.R
import li.songe.gkd.priv.PrivilegeServiceStatus
import li.songe.gkd.priv.privilegeServiceStatusFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.PrivilegeServiceRoute
import li.songe.gkd.ui.component.AppAlertDialog
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.SettingsDialog
import li.songe.gkd.ui.component.SubsItemCard
import li.songe.gkd.ui.component.TextMenu
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.component.rememberMultiSelectionState
import li.songe.gkd.ui.component.rememberPinnedListScrollState
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.Loadable
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.itemVerticalPadding
import li.songe.gkd.ui.style.surfaceCardColors
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.SubscriptionResult
import li.songe.gkd.util.UpdateTimeOption
import li.songe.gkd.util.findOption
import li.songe.gkd.util.getUpDownTransform
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Serializable
data object HomeRoute : NavKey

@Composable
fun HomePage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val state = loadableState.value
    val subItems = state?.subItems.orEmpty()
    val refreshing = state?.refreshing == true
    val settingsDialogVisible by vm.settingsDialogVisibleFlow.collectAsStateWithLifecycle()
    val powerWarningItem by vm.powerWarningItemFlow.collectAsStateWithLifecycle()
    val scope = vm.scope

    // 多选是订阅区块的临时交互状态，订阅项变化时按 id 收敛，不要改为可保存状态。
    val selectionState = rememberMultiSelectionState<Long>()
    val selectedIds = selectionState.selectedKeys
    val isSelectedMode = selectionState.active
    BackHandler(isSelectedMode) {
        selectionState.clear()
    }
    LaunchedEffect(subItems) {
        if (subItems.size <= 1) {
            selectionState.clear()
        } else {
            selectionState.retain(subItems.mapTo(mutableSetOf()) { it.id })
        }
    }

    if (settingsDialogVisible) {
        val store by storeFlow.collectAsStateWithLifecycle()
        SettingsDialog(
            onDismissRequest = { vm.setSettingsDialogVisible(false) },
            title = "订阅设置",
        ) {
            TextMenu(
                title = "更新订阅",
                option = UpdateTimeOption.objects.findOption(store.updateSubsInterval),
                onOptionChange = { vm.setUpdateInterval(it.value) },
            )
            TextSwitch(
                title = "耗电警告",
                subtitle = "启用多条订阅时弹窗确认",
                checked = store.subsPowerWarn,
                onCheckedChange = throttle(fn = vm::setPowerWarningEnabled),
            )
        }
    }

    powerWarningItem?.let {
        AppAlertDialog(
            title = { Text(text = "耗电警告") },
            text = {
                Column {
                    Text(text = "启用多个远程订阅可能导致执行大量重复规则, 这可能造成规则执行卡顿以及多余耗电\n\n请认真考虑后再确认开启！！！\n")
                    Text(
                        text = "查看耗电说明",
                        modifier = Modifier
                            .clickable(onClick = throttle {
                                vm.dismissPowerWarning()
                                mainVm.openWebPage(ShortUrlSet.URL6)
                            }),
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            onDismissRequest = {},
            confirmButton = {
                TextButton(
                    onClick = throttle(vm::confirmPowerWarning),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = "仍然启用")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissPowerWarning) {
                    Text(text = "取消")
                }
            },
        )
    }

    val pageScrollState = rememberPinnedListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val lazyListState = pageScrollState.listState
    val pullToRefreshState = rememberPullToRefreshState()
    val privilegeServiceStatus by privilegeServiceStatusFlow.collectAsStateWithLifecycle()

    val onAddSubscription: () -> Unit = {
        if (refreshing) {
            toast("正在刷新订阅,请稍后操作")
        } else {
            scope.launchTry {
                val url = mainVm.subsLinkDialog.request() ?: return@launchTry
                vm.addOrModifySubscription(url).message?.let { toast(it) }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (isSelectedMode) {
                        PerfIconButton(
                            imageVector = PerfIcon.Close,
                            contentDescription = "取消选择",
                            onClick = selectionState::clear,
                        )
                    }
                },
                title = {
                    if (isSelectedMode) {
                        Text(text = selectedIds.size.toString())
                    } else {
                        Text(text = stringResource(R.string.app_name))
                    }
                },
                actions = {
                    var expanded by remember { mutableStateOf(false) }
                    AnimatedContent(
                        targetState = isSelectedMode,
                        transitionSpec = { getUpDownTransform() },
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Row {
                            if (it) {
                                val text = "确定删除所选 ${selectedIds.size} 个订阅?"
                                PerfIconButton(
                                    imageVector = PerfIcon.Delete,
                                    contentDescription = "删除选中订阅",
                                    onClick = {
                                        scope.launchTry {
                                            if (!mainVm.dialogRequests.confirm(
                                                    title = "删除订阅",
                                                    text = text,
                                                    error = true,
                                                )) return@launchTry
                                            val result = vm.deleteSubscriptions(selectedIds)
                                            result.message?.let { toast(it) }
                                            if (result is SubscriptionResult.Success) {
                                                selectionState.clear()
                                            }
                                        }
                                    },
                                )
                                PerfIconButton(
                                    imageVector = PerfIcon.MoreVert,
                                    contentDescription = "更多操作",
                                    onClick = {
                                        if (refreshing) {
                                            toast("正在刷新订阅，请稍后操作")
                                        } else {
                                            expanded = true
                                        }
                                    })
                            } else {
                                val (contentDescription, contentColor) = when (privilegeServiceStatus) {
                                    PrivilegeServiceStatus.Connected -> "特权服务，已连接" to MaterialTheme.colorScheme.primary
                                    PrivilegeServiceStatus.Disconnected -> "特权服务，未连接" to MaterialTheme.colorScheme.onSurfaceVariant
                                    PrivilegeServiceStatus.DisconnectedDesired -> "特权服务，连接已中断" to MaterialTheme.colorScheme.error
                                }
                                PerfIconButton(
                                    imageVector = PerfIcon.RocketLaunch,
                                    onClickLabel = "前往特权服务页面",
                                    contentDescription = contentDescription,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = contentColor,
                                    ),
                                    onClick = throttle {
                                        mainVm.navigatePage(PrivilegeServiceRoute)
                                    },
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.wrapContentSize(Alignment.TopStart)
                    ) {
                        key(isSelectedMode) {
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                if (isSelectedMode) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = "全选")
                                        },
                                        onClick = {
                                            expanded = false
                                            selectionState.selectAll(subItems.map { it.id })
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = "反选")
                                        },
                                        onClick = {
                                            expanded = false
                                            selectionState.invert(subItems.map { it.id })
                                        }
                                    )
                                }
                            }
                        }
                    }
                })
        },
    ) { contentPadding ->
        PullToRefreshBox(
            modifier = Modifier.padding(contentPadding),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(itemHorizontalPadding / 2),
            ) {
                item("service-status") {
                    ServiceStatusSection()
                }
                item("trigger-record") {
                    TriggerRecordSection()
                }
                item("subs-header") {
                    SubsSectionHeader()
                }
                if (state == null) {
                    item("subs-state") {
                        SubsStateText(loadableState)
                    }
                } else {
                    itemsIndexed(state.subItems, key = { _, subItem -> subItem.id }) { index, subItem ->
                        SubsItemCard(
                            subsItem = subItem,
                            subscription = state.subscriptions[subItem.id],
                            index = index + 1,
                            isSelectedMode = isSelectedMode,
                            isSelected = selectedIds.contains(subItem.id),
                            loadError = state.loadErrors[subItem.id],
                            refreshError = state.refreshErrors[subItem.id],
                            refreshing = refreshing,
                            onOpen = {
                                mainVm.subsSheet.show(subItem.id)
                            },
                            onCheckedChange = { checked ->
                                vm.requestSubscriptionEnabled(subItem, checked)
                            },
                            onSelectedChange = { selectionState.toggle(subItem.id) },
                        )
                    }
                }
                if (state != null && !isSelectedMode) {
                    item("add-subs") {
                        AddSubsItemCard(onClick = onAddSubscription)
                    }
                }
                item("settings") {
                    SettingsSection()
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }
}

@Composable
private fun AddSubsItemCard(onClick: () -> Unit) {
    Card(
        onClick = throttle(onClick),
        modifier = Modifier
            .padding(horizontal = itemHorizontalPadding)
            .semantics { this.onClick(label = "打开添加订阅弹窗", action = null) },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(itemVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PerfIcon(
                imageVector = PerfIcon.Add,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(6.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(itemHorizontalPadding / 2))
            Text(
                text = "添加订阅",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SubsSectionHeader() {
    Text(
        text = "订阅",
        modifier = Modifier.titleItemPadding(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SubsStateText(state: Loadable<HomeSubsUiState>) {
    val error = (state as? Loadable.Failure)?.cause
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(itemVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = error?.message ?: if (error == null) "加载中..." else "数据加载失败",
            color = if (error == null) {
                LocalContentColor.current
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}
