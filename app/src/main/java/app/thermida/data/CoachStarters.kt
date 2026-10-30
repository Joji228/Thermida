package app.thermida.data

import app.thermida.domain.Macros
import app.thermida.domain.Targets
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The three questions offered on an empty coach chat, picked on the phone from the user's own
 * day (no AI request): what's most useful right now comes first, the usual questions fill the rest.
 */
object CoachStarters {
    val DEFAULTS = listOf(
        "How am I doing this week?",
        "Am I eating enough protein?",
        "Explain my last weigh-in",
    )

    fun pick(now: LocalDateTime, targets: Targets?, eatenToday: Macros, lastWeighIn: LocalDate?): List<String> {
        val hour = now.hour
        val today = now.toLocalDate()
        val timely = buildList {
            if (eatenToday.kcal <= 0.0) {
                add(if (hour < 11) "What's a good breakfast for my goal?" else "What should I eat today?")
            } else if (targets != null) {
                when {
                    eatenToday.kcal > targets.kcal -> add("I went over today. What now?")
                    hour >= 15 && eatenToday.protein < targets.protein * 0.5 -> add("How can I hit my protein today?")
                    hour >= 17 -> add("What should I have for dinner?")
                }
            }
            if (lastWeighIn == null) {
                add("When should I weigh myself?")
            } else if (lastWeighIn.isBefore(today.minusDays(7))) {
                add("How's my weight trending?")
            }
        }
        val defaults = if (lastWeighIn == null) DEFAULTS.filterNot { "weigh-in" in it } else DEFAULTS
        return (timely + defaults).distinct().take(3)
    }
}
