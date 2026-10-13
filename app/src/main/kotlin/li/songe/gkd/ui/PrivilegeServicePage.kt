package li.songe.gkd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import li.songe.gkd.priv.privilegeDesiredEnabledFlow
import li.songe.gkd.priv.shizuku.ShizukuPrivilegeController
import li.songe.gkd.ui.component.AppAlertDialog
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.throttle
import priv.kit.core.Privilege

@Serializable
data object PrivilegeServiceRoute : NavKey

@Composable
fun PrivilegeServicePage() {
    val mainVm = LocalMainViewModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverInfo by Privilege.serverState.collectAsStateWithLifecycle()
    val desired by privilegeDesiredEnabledFlow.collectAsStateWithLifecycle()
    var shizukuState by remember { mutableStateOf(ShizukuPrivilegeController.state()) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(serverInfo) {
        shizukuState = ShizukuPrivilegeController.state()
    }

    if (showInfoDialog) {
        PrivilegeServiceInfoDialog(onDismissRequest = { showInfoDialog = false })
    }

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = {
                    Text(text = "特权服务")
                },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.Info,
                        contentDescription = "页面说明",
                        onClick = throttle {
                            showInfoDialog = true
                        },
                    )
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = itemHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "服务状态",
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            StatusCard(
                title = if (serverInfo != null) "特权服务已连接" else "特权服务未连接",
                desc = when {
                    serverInfo != null -> "uid=${serverInfo?.uid}  pid=${serverInfo?.pid}"
                    desired -> "已配置自动连接，等待 Shizuku 或应用启动后重试"
                    else -> "点击下方按钮启动特权服务"
                },
                error = serverInfo == null && desired,
            )

            Text(
                text = "Shizuku",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            StatusCard(
                title = shizukuState.message,
                desc = buildString {
                    if (shizukuState.uid != null) {
                        append("uid=${shizukuState.uid}")
                    }
                    if (shizukuState.version != null) {
                        if (isNotEmpty()) append("  ")
                        append("version=${shizukuState.version}")
                    }
                }.takeIf { it.isNotEmpty() } ?: "未检测到 Shizuku 运行信息",
                error = !shizukuState.available || !shizukuState.authorized,
            )

            errorText?.let { text ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (shizukuState.available && !shizukuState.authorized) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                shizukuState = ShizukuPrivilegeController.requestAuthorization()
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(text = "授权 Shizuku")
                }
            }
            if (serverInfo == null) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && shizukuState.authorized,
                    onClick = {
                        scope.launch {
                            busy = true
                            errorText = null
                            try {
                                ShizukuPrivilegeController.start(context)
                            } catch (e: Throwable) {
                                errorText = e.message ?: e.javaClass.name
                            } finally {
                                busy = false
                                shizukuState = ShizukuPrivilegeController.state()
                            }
                        }
                    },
                ) {
                    Text(text = if (busy) "启动中..." else "启动特权服务")
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = {
                        ShizukuPrivilegeController.stop()
                    },
                ) {
                    Text(text = "停止特权服务")
                }
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = throttle {
                    shizukuState = ShizukuPrivilegeController.state()
                },
            ) {
                Text(text = "刷新状态")
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    desc: String?,
    error: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (!desc.isNullOrEmpty()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PrivilegeServiceInfoDialog(onDismissRequest: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "特权服务")
        },
        text = {
            Text(
                text = "此页面用于启动和管理 Shizuku 特权服务。连接后可为 GKD 提供写入安全设置授权、应用列表权限等系统级能力；断开后依赖特权服务的功能将不可用。\n\n使用前请确保 Shizuku 正在运行并已授权本应用。",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "我知道了")
            }
        },
    )
}
