package li.songe.gkd.data

sealed class ResolvedGroup(
    open val group: RawSubscription.RawGroupProps,
    val subscription: RawSubscription,
    val subsItem: SubsItem,
    val config: SubsConfig?,
) {
    val excludeData by lazy { ExcludeData.parse(config?.exclude) }

    abstract val appId: String?
}

class ResolvedAppGroup(
    override val group: RawSubscription.RawAppGroup,
    subscription: RawSubscription,
    subsItem: SubsItem,
    config: SubsConfig?,
    val app: RawSubscription.RawApp,
    val enable: Boolean,
) : ResolvedGroup(group, subscription, subsItem, config) {
    override val appId: String?
        get() = app.id
}