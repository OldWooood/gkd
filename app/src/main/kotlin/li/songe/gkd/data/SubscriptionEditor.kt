package li.songe.gkd.data

class SubscriptionEditor(subscription: RawSubscription) {
    private val subscriptionId = subscription.id
    private var current = subscription

    fun updateApp(
        appId: String,
        transform: RawSubscription.RawApp.() -> RawSubscription.RawApp,
    ): Boolean {
        val newApps = current.apps.updateFirstOrNull(
            predicate = { it.id == appId },
            transform = { it.transform() },
        ) ?: return false
        if (newApps !== current.apps) {
            setCurrent(current.copy(apps = newApps))
        }
        return true
    }

    private fun removeApp(appId: String): RawSubscription.RawApp? {
        val app = current.apps.find { it.id == appId } ?: return null
        setCurrent(current.copy(apps = current.apps.filterNot { it.id == appId }))
        return app
    }

    fun removeAppGroups(
        appId: String,
        removeAppIfEmpty: Boolean = false,
        predicate: (RawSubscription.RawAppGroup) -> Boolean,
    ): List<RawSubscription.RawAppGroup> {
        val app = current.apps.find { it.id == appId } ?: return emptyList()
        val (removed, remaining) = app.groups.partition(predicate)
        if (removed.isEmpty()) return emptyList()
        if (removeAppIfEmpty && remaining.isEmpty()) {
            removeApp(appId)
        } else {
            updateApp(appId) { copy(groups = remaining) }
        }
        return removed
    }

    fun build(): RawSubscription = current

    private fun setCurrent(subscription: RawSubscription) {
        require(subscription.id == subscriptionId) {
            "订阅id不可修改: $subscriptionId -> ${subscription.id}"
        }
        current = subscription
    }
}

fun RawSubscription.edit(
    block: SubscriptionEditor.() -> Unit,
): RawSubscription = SubscriptionEditor(this).apply(block).build()

private inline fun <T> List<T>.updateFirstOrNull(
    predicate: (T) -> Boolean,
    transform: (T) -> T,
): List<T>? {
    val index = indexOfFirst(predicate)
    if (index < 0) return null
    val current = this[index]
    val updated = transform(current)
    return if (updated == current) {
        this
    } else {
        toMutableList().apply { set(index, updated) }
    }
}
