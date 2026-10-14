package li.songe.gkd.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.AboutRoute
import li.songe.gkd.ui.component.AppAlertDialog
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.HOME_PAGE_URL
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Composable
fun SettingsSection() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    val store by storeFlow.collectAsStateWithLifecycle()
    val showToastInputDlg by vm.showActionToastDialogFlow.collectAsStateWithLifecycle()
    val actionScope = vm.scope

    if (showToastInputDlg) {
        var value by remember {
            mutableStateOf(store.actionToast)
        }
        val maxCharLen = 64
        AppAlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "触发提示")
                    PerfIconButton(
                        imageVector = PerfIcon.HelpOutline,
                        contentDescription = "文案规则",
                        onClickLabel = "打开文案规则弹窗",
                        onClick = throttle {
                            actionScope.launchTry {
                                mainVm.dialogRequests.showMessage(
                                    title = "文案规则",
                                    text = $$"触发文案支持变量替换，规则如下\n${1} 子规则名称\n${2} 规则名称\n${3} 触发次数\n\n示例模板\n${1}/${2}/${3}\n\n替换结果\n子规则a/规则A/3",
                                )
                            }
                        },
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = value,
                    placeholder = {
                        Text(text = "请输入提示内容")
                    },
                    onValueChange = {
                        value = it.take(maxCharLen)
                    },
                    supportingText = {
                        Text(
                            text = "${value.length} / $maxCharLen",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus()
                )
            },
            onDismissRequest = { vm.setActionToastDialogVisible(false) },
            confirmButton = {
                TextButton(enabled = value.isNotEmpty(), onClick = {
                    if (vm.saveActionToast(value)) {
                        toast("更新成功")
                    }
                    vm.setActionToastDialogVisible(false)
                }) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setActionToastDialogVisible(false) }) {
                    Text(text = "取消")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "常规",
            modifier = Modifier.titleItemPadding(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        TextSwitch(
            title = "规则匹配",
            subtitle = "关闭后不再执行任何规则",
            checked = store.enableMatch,
            onCheckedChange = { vm.setEnableMatch(it) },
        )
        TextSwitch(
            title = "触发提示",
            subtitle = store.actionToast,
            checked = store.toastWhenClick,
            onClickLabel = "打开触发提示弹窗",
            onClick = {
                vm.setActionToastDialogVisible(true)
            },
            onCheckedChange = {
                vm.setToastWhenClick(it)
            })
        TextSwitch(
            title = "后台隐藏",
            subtitle = "在「最近任务」隐藏卡片",
            checked = store.excludeFromRecents,
            onCheckedChange = { enabled ->
                actionScope.launchTry {
                    if (enabled) {
                        if (!mainVm.dialogRequests.confirm(
                                title = "后台隐藏",
                                text = "隐藏卡片后可能导致部分设备无法给任务卡片加锁后台，建议先加锁后再隐藏，若已加锁或没有锁后台机制请继续",
                                confirmText = "继续",
                            )) return@launchTry
                    }
                    vm.setExcludeFromRecents(enabled)
                }
            })

        Text(
            text = "其他",
            modifier = Modifier.titleItemPadding(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        SettingItem(
            title = "订阅设置",
            onClickLabel = "打开订阅设置弹窗",
            onClick = {
                vm.setSettingsDialogVisible(true)
            })

        SettingItem(title = "关于", onClick = {
            mainVm.navigatePage(AboutRoute)
        })

        SettingItem(
            title = "了解 GKD",
            subtitle = "查阅规则文档和常见问题",
            onClickLabel = "打开 GKD 文档页面",
            onClick = {
                mainVm.openWebPage(HOME_PAGE_URL)
            })
    }
}
