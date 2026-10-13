package li.songe.gkd.ui.home

import kotlinx.coroutines.flow.combine
import li.songe.gkd.store.actionCountFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.ruleSummaryFlow

class DashboardVm : BaseViewModel() {

    val subsStatusFlow = combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
        getSubsStatus(ruleSummary, count)
    }.stateInit(getSubsStatus(ruleSummaryFlow.value, actionCountFlow.value))
}
