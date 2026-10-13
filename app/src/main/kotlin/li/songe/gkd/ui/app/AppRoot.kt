package li.songe.gkd.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.AppTheme

@Composable
fun AppRoot() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            MainNavigation()
            AppOverlayHost()
        }
    }
}
