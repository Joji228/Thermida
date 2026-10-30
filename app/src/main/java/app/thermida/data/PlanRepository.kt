package app.thermida.data

import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import app.thermida.data.db.WeightEntity
import app.thermida.domain.AdaptiveEstimate
import app.thermida.domain.AdaptiveTdee
import app.thermida.domain.DayIntake
import app.thermida.domain.Nutrition
import app.thermida.domain.Targets
import app.thermida.domain.TrendPoint
import app.thermida.domain.UserProfile
import app.thermida.domain.WeighIn
import app.thermida.domain.WeightTrend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate

/** Everything the targets depend on, computed in one place so every screen agrees. */
data class PlanState(
    val profile: UserProfile? = null,
    val weighIns: List<WeightEntity> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    /** Smoothed trend weight, or the starting weight before any weigh-in. */
    val currentWeight: Double? = null,
    /** Null when smart target is off. */
    val estimate: AdaptiveEstimate? = null,
    val tdeeScale: Double = 1.0,
    val targets: Targets? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanRepository(
    private val settings: SettingsRepository,
    private val repository: ThermidaRepository,
    clock: DayClock,
    /** App-wide scope the shared plan lives in while anything is watching it. */
    scope: CoroutineScope,
) {

    // Rebuilt when the day changes, so the smart target's 4-week window moves with the calendar.
    // Worked out once on a background thread and shared: the dashboard, Body screen, Settings and
    // the scale all read the same result instead of each recomputing it on the main thread.
    val plan: Flow<PlanState> = clock.today.flatMapLatest { today ->
        combine(
            settings.profile,
            repository.weights(),
            repository.dailyKcal(today.minusDays(AdaptiveTdee.WINDOW_DAYS + 7L), today),
        ) { profile, weights, daily ->
            val weighIns = weights.map { WeighIn(LocalDate.parse(it.date), it.kg) }
            val trend = WeightTrend.compute(weighIns)
            val current = trend.lastOrNull()?.trend ?: profile?.startWeightKg
            if (profile == null || current == null) return@combine PlanState(profile, weights, trend, current)

            val formula = Nutrition.tdee(profile, current)
            val estimate = if (profile.smartTarget) {
                AdaptiveTdee.estimate(weighIns, daily.map { DayIntake(LocalDate.parse(it.date), it.kcal) }, formula, today)
            } else {
                null
            }
            val scale = estimate?.let { AdaptiveTdee.blend(formula, it) / formula } ?: 1.0
            PlanState(profile, weights, trend, current, estimate, scale, Nutrition.targets(profile, current, scale))
        }
    }
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
}
