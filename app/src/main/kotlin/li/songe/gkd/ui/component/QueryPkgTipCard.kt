package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import li.songe.gkd.ui.PrivilegeServiceRoute
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.util.throttle

@Composable
fun QueryPkgAuthCard(
    modifier: Modifier = Modifier,
) {
    val mainVm = LocalMainViewModel.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PerfIcon(
            imageVector = PerfIcon.WarningAmber,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "如需显示所有应用\n请连接特权服务自动授权",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(
            onClick = throttle {
                mainVm.navigatePage(PrivilegeServiceRoute)
            }
        ) {
            Text(text = "前往特权服务")
        }
    }
}
