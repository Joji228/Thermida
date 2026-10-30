package app.thermida.ui.dashboard

import kotlinx.coroutines.flow.map
import app.thermida.voice.VoiceLanguage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thermida.AppContainer
import app.thermida.ai.AiItem
import app.thermida.ai.AnalysisResult
import app.thermida.ai.enrichFromFoodFacts
import app.thermida.data.PlanState
import app.thermida.data.db.MealWithItems
import app.thermida.data.db.macros
import app.thermida.domain.MealType
import app.thermida.data.db.FoodItemEntity
import app.thermida.data.db.MealEntity
import app.thermida.data.EntryTime
import app.thermida.data.FoodSource
import app.thermida.data.FoodFact
import app.thermida.domain.Macros
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    /** The real current day; changes at midnight. */
    val today: LocalDate = LocalDate.now(),
    val plan: PlanState = PlanState(),
    val eaten: Macros = Macros(),
    val waterMl: Int = 0,
    val meals: List<MealWithItems> = emptyList(),
) {
    val profile get() = plan.profile
    val targets get() = plan.targets
    val currentWeight get() = plan.currentWeight
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val app: AppContainer) : ViewModel() {
    private val date = app.viewedDay

    // The date travels inside each emission, so the day and its meals always change together;
    // otherwise a swipe could briefly show the new date over the old day's meals.
    val state: StateFlow<DashboardState> = date.flatMapLatest { day ->
        combine(
            app.plans.plan,
            app.repository.mealsFor(day),
            app.repository.waterFor(day),
            app.clock.today,
        ) { plan, meals, water, today ->
            DashboardState(
                loading = false,
                date = day,
                today = today,
                plan = plan,
                eaten = meals.fold(Macros()) { acc, meal -> acc + meal.macros() },
                waterMl = water,
                meals = meals,
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    /** A write that didn't work, for the snackbar: a full disk or a database error shouldn't crash the app. */
    val problems = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private fun write(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problems.tryEmit("Couldn't save that. ${e.message.orEmpty()}".trim())
        }
    }

    fun previousDay() {
        date.value = date.value.minusDays(1)
    }

    fun nextDay() {
        if (date.value < app.clock.today.value) date.value = date.value.plusDays(1)
    }

    fun addWater(ml: Int) {
        write { app.repository.addWater(date.value, ml) }
    }

    fun undoWater() {
        write { app.repository.undoWater(date.value) }
    }

    /** A typed-in weigh-in for [date], the day being viewed. The smart scale only ever logs today. */
    fun logWeight(kg: Double, date: LocalDate) {
        write {
            val today = app.clock.today.value
            if (date == today) app.repository.logWeight(kg) else app.repository.logWeight(kg, date, EntryTime.stamp(date, EntryTime.WEIGH_IN, today))
        }
    }

    /** Off by default; the Body screen and composition line only appear once it's turned on in Settings. */
    val scaleEnabled = app.settings.scaleEnabled

    /** The language the mic in Log food listens for: the same choice as the coach's mic. */
    val voiceLanguage = app.settings.voiceLanguage.map { VoiceLanguage.pick(it) }

    fun setVoiceLanguage(language: VoiceLanguage) = write { app.settings.setVoiceLanguage(language.name) }

    /** Quick add is offered only to users who turned on Open Food Facts. */
    val foodFactsEnabled = app.settings.useFoodFacts

    /**
     * A plain-language description, estimated by the AI engine (DeepSeek by default, or its backup).
     * Branded products then take their real label values from Open Food Facts when it is on.
     */
    suspend fun describeFood(text: String): AnalysisResult {
        val result = app.ai.analyze(emptyList(), text)
        return if (app.settings.useFoodFacts.first()) result.enrichFromFoodFacts(app.foodFacts) else result
    }

    /**
     * Logs a meal from the Add food box as one meal with its items, as corrected on screen.
     * [aiSource] is null when the foods came from recent foods only.
     */
    fun logDescribed(name: String, items: List<AiItem>, type: MealType, date: LocalDate, aiSource: String?, notes: String?) {
        write {
            app.repository.saveMeal(
                MealEntity(
                    date = date.toString(),
                    timestamp = EntryTime.stamp(date, EntryTime.meal(type), today = app.clock.today.value),
                    mealType = type.name,
                    name = name.ifBlank { "Meal" }.take(60),
                    photoPath = null,
                    source = if (aiSource == null) "Recent foods" else FoodSource.withFoodFacts(aiSource, items.any { it.foodFacts != null }).orEmpty(),
                    note = notes?.ifBlank { null },
                ),
                items.filter { it.grams > 0 }.map {
                    FoodItemEntity(
                        mealId = 0,
                        name = it.name.ifBlank { "Food" },
                        grams = it.grams,
                        kcalPer100 = it.kcalPer100,
                        proteinPer100 = it.proteinPer100,
                        carbsPer100 = it.carbsPer100,
                        fatPer100 = it.fatPer100,
                    )
                },
            )
        }
    }

    /** Foods logged in the last month, newest first, one per name: starting points for the Add food box. */
    suspend fun recentFoods(): List<AiItem> =
        app.repository.recentFoodItems(app.clock.today.value.minusDays(30))
            .distinctBy { it.name.trim().lowercase() }
            .take(12)
            .map {
                AiItem(
                    name = it.name,
                    grams = it.grams,
                    kcalPer100 = it.kcalPer100,
                    proteinPer100 = it.proteinPer100,
                    carbsPer100 = it.carbsPer100,
                    fatPer100 = it.fatPer100,
                )
            }

    /** Deletes a meal and hands it back, so the undo in the snackbar can put it back. */
    suspend fun removeMeal(id: Long): MealWithItems? {
        return try {
            val meal = app.repository.meal(id)
            app.repository.deleteMeal(id, keepPhoto = true)
            meal
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problems.tryEmit("Couldn't delete the meal. ${e.message.orEmpty()}".trim())
            null
        }
    }

    /** Puts back a meal removed by [removeMeal], photo included. */
    fun restoreMeal(meal: MealWithItems) {
        write {
            app.repository.saveMeal(
                meal.meal.copy(id = 0, photoPath = meal.meal.photoPath?.takeIf { java.io.File(it).exists() }),
                meal.items.map { it.copy(id = 0, mealId = 0) },
            )
        }
    }

    /** Logs one Open Food Facts product as its own meal, on [date] (the day being viewed). */
    fun quickLog(fact: FoodFact, name: String, grams: Double, type: MealType, date: LocalDate) {
        logMeal(
            date, type, name, "Open Food Facts",
            FoodItemEntity(
                mealId = 0,
                name = fact.label,
                grams = grams,
                kcalPer100 = fact.kcalPer100,
                proteinPer100 = fact.proteinPer100,
                carbsPer100 = fact.carbsPer100,
                fatPer100 = fact.fatPer100,
            ),
        )
    }

    /**
     * A food typed in by hand: its total calories and macros. Stored as a 100 g item so the
     * per-100 g values are the totals and editing it later works like any other meal.
     */
    fun manualLog(name: String, kcal: Double, protein: Double, carbs: Double, fat: Double, type: MealType, date: LocalDate) {
        logMeal(
            date, type, name, FoodSource.BY_HAND,
            FoodItemEntity(mealId = 0, name = name, grams = 100.0, kcalPer100 = kcal, proteinPer100 = protein, carbsPer100 = carbs, fatPer100 = fat),
        )
    }

    private fun logMeal(date: LocalDate, type: MealType, name: String, source: String, item: FoodItemEntity) {
        write {
            app.repository.saveMeal(
                MealEntity(
                    date = date.toString(),
                    timestamp = EntryTime.stamp(date, EntryTime.meal(type), today = app.clock.today.value),
                    mealType = type.name,
                    name = name,
                    photoPath = null,
                    source = source,
                    note = null,
                ),
                listOf(item),
            )
        }
    }

    fun deleteWeight(id: Long) {
        write { app.repository.deleteWeight(id) }
    }
}
