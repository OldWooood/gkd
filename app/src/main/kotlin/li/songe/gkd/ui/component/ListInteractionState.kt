package li.songe.gkd.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
class MultiSelectionState<K> {
    var selectedKeys by mutableStateOf<Set<K>>(emptySet())
        private set

    val active: Boolean
        get() = selectedKeys.isNotEmpty()

    fun selectOnly(key: K) {
        selectedKeys = setOf(key)
    }

    fun toggle(key: K) {
        selectedKeys = if (key in selectedKeys) {
            selectedKeys - key
        } else {
            selectedKeys + key
        }
    }

    fun selectAll(keys: Iterable<K>) {
        selectedKeys = keys.toSet()
    }

    fun invert(keys: Iterable<K>) {
        selectedKeys = keys.toSet() - selectedKeys
    }

    fun retain(keys: Set<K>) {
        val retainedKeys = selectedKeys intersect keys
        if (retainedKeys != selectedKeys) {
            selectedKeys = retainedKeys
        }
    }

    fun clear() {
        selectedKeys = emptySet()
    }
}

@Composable
fun <K> rememberMultiSelectionState(): MultiSelectionState<K> = remember {
    MultiSelectionState()
}
