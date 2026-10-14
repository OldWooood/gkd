package li.songe.gkd.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.switchAutomatorService
import li.songe.gkd.ui.ActionLogRoute
import li.songe.gkd.ui.PrivilegeServiceRoute
import li.songe.gkd.ui.component.GroupNameText
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfSwitch
import li.songe.gkd.ui.component.textSize
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.itemVerticalPadding
import li.songe.gkd.ui.style.surfaceCardColors
import li.songe.gkd.util.latestRecordDescFlow
import li.songe.gkd.util.latestRecordFlow
import li.songe.gkd.util.openA11ySettings
import li.songe.gkd.util.throttle

@Composable
fun ServiceStatusSection() {
    val mainVm = LocalMainViewModel.current
    val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
    val writeSecureSettings by PermissionStates.writeSecureSettings.stateFlow.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.padding(horizontal = itemHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(itemHorizontalPadding / 2)
    ) {
        if (PermissionStates.appOpsRestrictedFlow.collectAsStateWithLifecycle().value) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        this.onClick(label = "前往特权服务页面", action = null)
                    },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                onClick = throttle {
                    mainVm.navigatePage(PrivilegeServiceRoute)
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(itemVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PerfIcon(imageVector = PerfIcon.WarningAmber)
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "检测到权限受限，请前往特权服务",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    PerfIcon(imageVector = PerfIcon.KeyboardArrowRight)
                }
            }
        }
        ServiceStatusCard(
            subtitle = if (a11yRunning) {
                "无障碍正在运行"
            } else if (mainVm.a11yServiceEnabledFlow.collectAsStateWithLifecycle().value) {
                "无障碍发生故障"
            } else if (writeSecureSettings) {
                "无障碍已关闭"
            } else {
                "无障碍未授权"
            },
            checked = a11yRunning,
            onCheckedChange = { newEnabled ->
                if (newEnabled && !PermissionStates.writeSecureSettings.value) {
                    openA11ySettings()
                } else {
                    switchAutomatorService()
                }
            },
        )
    }
}

@Composable
fun TriggerRecordSection() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    val subsStatus by vm.subsStatusFlow.collectAsStateWithLifecycle()
    val latestRecord by latestRecordFlow.collectAsStateWithLifecycle()
    val latestRecordDesc by latestRecordDescFlow.collectAsStateWithLifecycle()
    TriggerOverviewCard(
        modifier = Modifier.padding(horizontal = itemHorizontalPadding),
        subsStatus = subsStatus,
        latestRecordDesc = latestRecordDesc,
        latestRecordIsGlobal = latestRecord?.groupType == SubsConfig.GlobalGroupType,
        onOpenActionLog = { mainVm.navigatePage(ActionLogRoute()) },
        onOpenLatestRecord = {
            latestRecord?.let {
                mainVm.navigatePage(ActionLogRoute(appId = it.appId))
            }
        },
    )
}

@Composable
private fun ServiceStatusCard(
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val onStatusClick = throttle { onCheckedChange(!checked) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
    ) {
        IconTextCard(
            imageVector = PerfIcon.Memory,
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .clickable(
                    onClickLabel = "切换服务状态",
                    onClick = onStatusClick,
                ),
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "服务状态",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            PerfSwitch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun IconTextCard(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(itemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PerfIcon(
            imageVector = imageVector,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp)
                .size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(itemHorizontalPadding))
        content()
    }
}

@Composable
private fun TriggerOverviewCard(
    subsStatus: String,
    latestRecordDesc: String?,
    latestRecordIsGlobal: Boolean,
    onOpenActionLog: () -> Unit,
    onOpenLatestRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = surfaceCardColors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .clickable(
                    onClickLabel = "打开触发记录页面",
                    onClick = throttle(onOpenActionLog),
                )
                .padding(
                    start = itemVerticalPadding,
                    end = itemVerticalPadding,
                    top = itemVerticalPadding,
                    bottom = itemVerticalPadding / 2
                ), verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Equalizer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(itemHorizontalPadding))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "触发记录",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "规则误触可定位关闭",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = itemVerticalPadding)
        ) {
            AnimatedVisibility(subsStatus.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = subsStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (latestRecordDesc != null) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(
                            onClickLabel = "前往应用的规则汇总页面",
                            onClick = throttle(onOpenLatestRecord),
                        )
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        GroupNameText(
                            modifier = Modifier.fillMaxWidth(),
                            preText = "最近触发: ",
                            isGlobal = latestRecordIsGlobal,
                            text = latestRecordDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    PerfIcon(
                        imageVector = PerfIcon.KeyboardArrowRight,
                        modifier = Modifier.textSize(style = MaterialTheme.typography.bodyMedium),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(itemVerticalPadding))
        }
    }
}
