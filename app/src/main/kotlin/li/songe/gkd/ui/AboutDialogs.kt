package li.songe.gkd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.META
import li.songe.gkd.ui.component.AppAlertDialog
import li.songe.gkd.util.format
import li.songe.gkd.util.openUri

@Composable
fun AboutDialogs() {
    VersionInfoDialog()
}

@Composable
private fun VersionInfoDialog() {
    val vm = viewModel<AboutVm>()
    val visible by vm.showInfoDlgFlow.collectAsStateWithLifecycle()
    if (visible) {
        AppAlertDialog(
            onDismissRequest = { vm.setInfoDialogVisible(false) },
            title = { Text(text = "版本信息") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(text = "构建渠道")
                        Text(text = META.channel)
                    }
                    Column {
                        Text(text = "版本代码")
                        Text(text = META.versionCode.toString())
                    }
                    Column {
                        Text(text = "版本名称")
                        Text(text = META.versionName)
                    }
                    Column {
                        Text(text = "代码记录")
                        Text(
                            modifier = Modifier.clickable { openUri(META.commitUrl) },
                            text = META.tagName ?: META.commitId.substring(0, 16),
                            color = MaterialTheme.colorScheme.primary,
                            style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                        )
                    }
                    Column {
                        Text(text = "提交时间")
                        Text(text = META.commitTime.format("yyyy-MM-dd HH:mm:ss ZZ"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setInfoDialogVisible(false) }) {
                    Text(text = "关闭")
                }
            },
        )
    }
}
