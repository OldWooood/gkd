package li.songe.gkd.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel

class SettingsVm : BaseViewModel() {

    val showActionToastDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setActionToastDialogVisible(visible: Boolean) {
        showActionToastDialogFlow.value = visible
    }

    fun saveActionToast(value: String): Boolean {
        if (value == storeFlow.value.actionToast) return false
        storeFlow.update { it.copy(actionToast = value) }
        return true
    }

    fun setToastWhenClick(enabled: Boolean) {
        storeFlow.update { it.copy(toastWhenClick = enabled) }
    }

    fun setExcludeFromRecents(enabled: Boolean) {
        storeFlow.update { it.copy(excludeFromRecents = enabled) }
    }
}
