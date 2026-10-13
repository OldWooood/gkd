package li.songe.gkd.ui.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import li.songe.gkd.ui.AboutPage
import li.songe.gkd.ui.AboutRoute
import li.songe.gkd.ui.ActionLogPage
import li.songe.gkd.ui.ActionLogRoute
import li.songe.gkd.ui.PrivilegeServicePage
import li.songe.gkd.ui.PrivilegeServiceRoute
import li.songe.gkd.ui.SubsAppGroupListPage
import li.songe.gkd.ui.SubsAppGroupListRoute
import li.songe.gkd.ui.SubsAppListPage
import li.songe.gkd.ui.SubsAppListRoute
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.home.HomePage
import li.songe.gkd.ui.home.HomeRoute

private val mainRouteEntryProvider = entryProvider {
    entry<HomeRoute> { HomePage() }
    entry<AboutRoute> { AboutPage() }
    entry<PrivilegeServiceRoute> { PrivilegeServicePage() }
    entry<SubsAppListRoute> { SubsAppListPage(it) }
    entry<ActionLogRoute> { ActionLogPage(it) }
    entry<SubsAppGroupListRoute> { SubsAppGroupListPage(it) }
}

@Composable
fun MainNavigation() {
    val mainVm = LocalMainViewModel.current
    NavDisplay(
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        backStack = mainVm.backStack,
        onBack = mainVm::popPage,
        entryProvider = mainRouteEntryProvider,
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },
    )
}
