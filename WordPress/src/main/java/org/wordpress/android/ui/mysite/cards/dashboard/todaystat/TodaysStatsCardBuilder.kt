package org.wordpress.android.ui.mysite.cards.dashboard.todaystat

import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.DashboardCards.DashboardCard.TodaysStatsCard
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.TodaysStatsCardBuilderParams
import org.wordpress.android.ui.stats.refresh.utils.StatsUtils
import javax.inject.Inject

@Suppress("TooManyFunctions")
class TodaysStatsCardBuilder @Inject constructor(
    private val statsUtils: StatsUtils,

        ) {
    fun build(params: TodaysStatsCardBuilderParams): TodaysStatsCard? {
        val stats = params.todaysStatsCard
        stats?.let {  return TodaysStatsCard(views = statsUtils.toFormattedString(stats.views),
                visitors = statsUtils.toFormattedString(stats.visitors),
                likes = statsUtils.toFormattedString(stats.likes))
        }
        return null
    }
}
