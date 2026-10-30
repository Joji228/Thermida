package app.thermida.data

import androidx.room.withTransaction
import app.thermida.data.db.FoodItemEntity
import app.thermida.data.db.MealEntity
import app.thermida.data.db.ThermidaDatabase
import app.thermida.data.db.WaterEntity
import app.thermida.data.db.WeightEntity
import app.thermida.domain.BodyComposition
import app.thermida.domain.UserProfile
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate

/** Everything the app stores, minus API keys and photos. Used for Export / Import backup. */
@Serializable
data class Backup(
    val version: Int = VERSION,
    val profile: UserProfile? = null,
    val meals: List<MealEntity> = emptyList(),
    val items: List<FoodItemEntity> = emptyList(),
    val water: List<WaterEntity> = emptyList(),
    val weights: List<WeightEntity> = emptyList(),
    /** Added in version 2. Version 1 files have none, and restoring them leaves settings as they are. */
    val settings: SettingsBackup? = null,
) {
    /** Nothing logged at all: a fresh install, or a phone that was just erased. */
    val isEmpty get() = meals.isEmpty() && water.isEmpty() && weights.isEmpty()

    /**
     * Why this file must not replace the data on the phone, or null when it can. Any JSON object
     * reads as a Backup (unknown fields are ignored), so a file with no profile and nothing
     * logged is some other file, and importing it would just erase everything.
     */
    fun problem(): String? {
        if (version > VERSION) return "This backup was made by a newer Thermida. Update the app first, then import it."
        if (isEmpty && profile == null) return "That file isn't a Thermida backup, or it has nothing in it. Nothing was changed."
        val mealIds = meals.map { it.id }.toSet()
        if (items.any { it.mealId !in mealIds }) return "That backup is damaged: some foods belong to no meal. Nothing was changed."
        return null
    }

    companion object {
        /** The newest backup format this version writes and reads. */
        const val VERSION = 2
    }
}

const val PHOTO_DAYS = 30

class ThermidaRepository(
    private val db: ThermidaDatabase,
    /** Where meal photos live; the only folder this class ever deletes files from. */
    private val mealDir: File,
) {
    private fun isMealPhoto(path: String?) = MealPhotos.isMealPhoto(path, mealDir)


    fun mealsFor(date: LocalDate) = db.meals().observeDay(date.toString())

    fun waterFor(date: LocalDate) = db.water().observeTotal(date.toString())

    fun weights() = db.weights().observeAll()

    fun dailyKcal(from: LocalDate, to: LocalDate) = db.meals().observeDailyKcal(from.toString(), to.toString())

    suspend fun deleteWeight(id: Long) = db.weights().delete(id)

    suspend fun meal(id: Long) = db.meals().get(id)

    suspend fun mealCount() = db.meals().count()

    /** Food lines logged since [since], newest first, for the Add food box's recent foods. */
    suspend fun recentFoodItems(since: LocalDate): List<FoodItemEntity> = db.meals().itemsSince(since.toString())

    suspend fun saveMeal(meal: MealEntity, items: List<FoodItemEntity>): Long = db.withTransaction {
        val id = if (meal.id == 0L) {
            db.meals().insert(meal)
        } else {
            db.meals().update(meal)
            db.meals().deleteItems(meal.id)
            meal.id
        }
        db.meals().insertItems(items.map { it.copy(id = 0, mealId = id) })
        id
    }

    /**
     * [keepPhoto] leaves the photo file in place so an undo can bring the meal back with it; if
     * nothing does, the photo is removed with the other unused ones on the next launch (see [prunePhotos]).
     */
    suspend fun deleteMeal(id: Long, keepPhoto: Boolean = false) {
        val photo = db.meals().get(id)?.meal?.photoPath
        db.meals().delete(id)
        if (!keepPhoto) photo?.takeIf(::isMealPhoto)?.let { File(it).delete() }
    }

    /**
     * Rolling photo window: photos older than [days] are deleted and their meals keep only
     * the numbers. Files no meal points to (e.g. from deleted meals) are removed too.
     */
    suspend fun prunePhotos(photos: List<File>, days: Int = PHOTO_DAYS) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val old = db.meals().withPhotosBefore(cutoff)
        old.forEach { meal -> meal.photoPath?.takeIf(::isMealPhoto)?.let { File(it).delete() } }
        if (old.isNotEmpty()) db.meals().clearPhotos(old.map { it.id })
        val referenced = db.meals().photoPaths().toSet()
        photos.filter { it.absolutePath !in referenced }.forEach { it.delete() }
        clearMissingPhotos()
    }

    /**
     * Meals pointing at photos that no longer exist (for example after importing a backup made
     * on another phone; backups don't carry photos) drop the link instead of showing an empty box.
     */
    suspend fun clearMissingPhotos() {
        // A link outside the photo folder (from a crafted or broken backup) is dropped as well.
        val missing = db.meals().withPhotosBefore(Long.MAX_VALUE).filter { m -> m.photoPath?.let { !isMealPhoto(it) || !File(it).exists() } == true }
        if (missing.isNotEmpty()) db.meals().clearPhotos(missing.map { it.id })
    }

    /** Deletes every saved meal photo; the meals stay logged with their numbers. */
    suspend fun deleteAllPhotos(photos: List<File>) {
        db.meals().clearAllPhotos()
        photos.forEach { it.delete() }
    }

    suspend fun addWater(date: LocalDate, ml: Int) =
        db.water().insert(WaterEntity(date = date.toString(), timestamp = System.currentTimeMillis(), ml = ml))

    suspend fun undoWater(date: LocalDate) = db.water().deleteLast(date.toString())

    suspend fun logWeight(kg: Double) = db.weights().insert(
        WeightEntity(date = LocalDate.now().toString(), timestamp = System.currentTimeMillis(), kg = kg),
    )

    /**
     * A typed-in weigh-in for another day, timed at [timestamp] (its morning). If that day already
     * has a later weigh-in, this one goes just after it: the latest weigh-in of a day is the one the
     * trend uses, and a weight added afterwards is usually a correction.
     */
    suspend fun logWeight(kg: Double, date: LocalDate, timestamp: Long): Long {
        val after = db.weights().latestOn(date.toString())?.let { it + 60_000 }
        return db.weights().insert(WeightEntity(date = date.toString(), timestamp = maxOf(timestamp, after ?: 0L), kg = kg))
    }

    /** Water for any day, stamped with the given time (the coach can log a past day's water). */
    suspend fun addWater(date: LocalDate, ml: Int, timestamp: Long) =
        db.water().insert(WaterEntity(date = date.toString(), timestamp = timestamp, ml = ml))

    /**
     * A weigh-in from the smart scale; [composition] is null when it was weighed without impedance.
     * Returns the new entry's id, so an automatic save can be undone.
     */
    suspend fun logScaleWeighIn(kg: Double, impedance: Int?, composition: BodyComposition?): Long = db.weights().insert(
        WeightEntity(
            date = LocalDate.now().toString(),
            timestamp = System.currentTimeMillis(),
            kg = kg,
            impedance = impedance,
            fatPct = composition?.fatPct,
            waterPct = composition?.waterPct,
            muscleKg = composition?.muscleKg,
            boneKg = composition?.boneKg,
            leanKg = composition?.leanKg,
        ),
    )

    suspend fun export(profile: UserProfile?, settings: SettingsBackup?) = Backup(
        profile = profile,
        settings = settings,
        meals = db.meals().allMeals(),
        items = db.meals().allItems(),
        water = db.water().all(),
        weights = db.weights().all(),
    )

    suspend fun restore(backup: Backup) = db.withTransaction {
        db.meals().clear()
        db.water().clear()
        db.weights().clear()
        // Photos never travel in a backup; any path in the file is kept only if it points into the photo folder.
        db.meals().insertMeals(backup.meals.map { if (isMealPhoto(it.photoPath)) it else it.copy(photoPath = null) })
        db.meals().insertItems(backup.items)
        db.water().insertAll(backup.water)
        db.weights().insertAll(backup.weights)
        clearMissingPhotos()
    }
}
