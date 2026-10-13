package li.songe.gkd.store

import kotlinx.serialization.Serializable
import li.songe.gkd.META
import li.songe.gkd.util.AppGroupOption
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.UpdateChannelOption
import li.songe.gkd.util.UpdateTimeOption

@Serializable
data class SettingsStore(
    val enableAutomator: Boolean = false,
    val enableMatch: Boolean = true,
    val excludeFromRecents: Boolean = false,
    val updateSubsInterval: Long = UpdateTimeOption.Everyday.value,
    val toastWhenClick: Boolean = true,
    val actionToast: String = META.appName,
    val updateChannel: Int = if (META.isBeta) UpdateChannelOption.Beta.value else UpdateChannelOption.Stable.value,
    val appSort: Int = AppSortOption.ByUsedTime.value,
    val showBlockApp: Boolean = true,
    val appRuleSort: Int = RuleSortOption.ByDefault.value,
    val subsAppSort: Int = AppSortOption.ByUsedTime.value,
    val subsAppGroupType: Int = AppGroupOption.UserGroup.value or AppGroupOption.SystemGroup.value,
    val subsAppShowBlock: Boolean = false,
    val subsPowerWarn: Boolean = true,
    val appGroupType: Int = (1 shl AppGroupOption.normalObjects.size) - 1,
    val showDisabledRule: Boolean = true,
)
