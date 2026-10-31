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
import app.thermida.domain.SmartSchedule
import app.thermida.domain.Targets
import app.thermida.domain.TrendPoint
import app.thermida.domain.UserProfile
import app.thermida.domain.WeighIn
import app.thermida.domain.WeightTrend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
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
    /** A weekly smart-target step taken just now, still to be saved (see [SmartSchedule]). */
    val smartStep: SmartStep? = null,
    /** Set on the first run with weekly steps: the correction to keep (null if none was working yet). */
    val smartHandOver: SmartHandOver? = null,
)

/** See [SettingsRepository.handOverSmart]. */
data class SmartHandOver(val applied: SmartSchedule.Applied?)

/** The smart target's new correction and what it did to the calorie target. */
data class SmartStep(val applied: SmartSchedule.Applied, val change: SmartSchedule.Change)

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
            settings.smartApplied,
            settings.smartHandedOver,
        ) { profile, weights, daily, stored, handedOver ->
            val weighIns = weights.map { WeighIn(LocalDate.parse(it.date), it.kg) }
            val trend = WeightTrend.compute(weighIns)
            val current = trend.lastOrNull()?.trend ?: profile?.startWeightKg
            if (profile == null || current == null) return@combine PlanState(profile, weights, trend, current)

            val formula = Nutrition.tdee(profile, current)
            val estimate = if (profile.smartTarget) {
                val started = runCatching { LocalDate.parse(profile.startDate) }.getOrNull()
                AdaptiveTdee.estimate(weighIns, daily.map { DayIntake(LocalDate.parse(it.date), it.kcal) }, formula, today, started)
            } else {
                null
            }
            // The target follows the measured burn only in weekly steps, and not at all while a
            // calorie target of your own is set.
            val steering = estimate != null && profile.customKcal == null
            val desired = estimate?.let { AdaptiveTdee.blend(formula, it) / formula } ?: 1.0
            if (!handedOver) {
                // Before weekly steps, a working smart target used today's measured burn directly:
                // carry on from exactly there, so updating the app doesn't move the target.
                val keep = if (steering && estimate?.active == true) SmartSchedule.Applied(desired, today) else null
                val scale = keep?.scale ?: 1.0
                return@combine PlanState(
                    profile, weights, trend, current, estimate, scale, Nutrition.targets(profile, current, scale),
                    smartHandOver = SmartHandOver(keep),
                )
            }
            val (applied, stepped) = if (steering) SmartSchedule.next(stored, desired, today) else null to false
            val scale = applied?.scale ?: 1.0
            val targets = Nutrition.targets(profile, current, scale)
            val step = if (stepped && applied != null) {
                val before = Nutrition.targets(profile, current, stored?.scale ?: 1.0).kcal
                SmartStep(applied, SmartSchedule.Change(today, before, targets.kcal))
            } else {
                null
            }
            PlanState(profile, weights, trend, current, estimate, scale, targets, step)
        }
    }
        // Saving the step brings the plan round again with it stored, so it's taken only once.
        .onEach { state ->
            state.smartHandOver?.let { settings.handOverSmart(it.applied) }
            state.smartStep?.let { settings.applySmartChange(it.applied, it.change) }
        }
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
}
