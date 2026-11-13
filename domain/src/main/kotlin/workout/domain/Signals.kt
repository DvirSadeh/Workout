package workout.domain

import java.time.LocalDate
import kotlin.math.abs

data class FamilyMemory(
    val familyId: String,
    val exerciseId: String,
    val tier: Int,
    val loadKg: Double?,
    val date: LocalDate,
    val hitTop: Boolean,
    val missed: Boolean,
    val anchor: Boolean,
)

data class AnchorLock(
    val pattern: MovementPattern,
    val exerciseId: String,
    val familyId: String,
)

object Signals {
    fun prior(history: List<SessionRecord>, today: LocalDate): List<SessionRecord> =
        history.filter { it.date < today }.sortedByDescending { it.date }

    fun memories(catalog: Catalog, history: List<SessionRecord>, today: LocalDate): List<FamilyMemory> =
        prior(history, today).flatMap { session ->
            session.exercises.mapNotNull { logged ->
                val exercise = catalog.find(logged.exerciseId) ?: return@mapNotNull null
                FamilyMemory(
                    familyId = exercise.familyId,
                    exerciseId = exercise.id,
                    tier = exercise.tier,
                    loadKg = logged.prescribedLoadKg,
                    date = session.date,
                    hitTop = logged.hitTop(),
                    missed = logged.missed(),
                    anchor = logged.anchor,
                )
            }
        }

    fun lastFamily(memories: List<FamilyMemory>, familyId: String): FamilyMemory? =
        memories.filter { it.familyId == familyId }.maxByOrNull { it.date }

    fun lastUse(memories: List<FamilyMemory>, exerciseId: String): FamilyMemory? =
        memories.filter { it.exerciseId == exerciseId }.maxByOrNull { it.date }

    fun locks(profile: UserProfile, catalog: Catalog, history: List<SessionRecord>, today: LocalDate): List<AnchorLock> {
        val block = Training.blockIndex(profile, today)
        val sessions = history
            .filter { it.date < today && Training.blockIndex(profile, it.date) == block }
            .sortedBy { it.date }
        val found = linkedMapOf<MovementPattern, AnchorLock>()
        for (session in sessions) {
            for (logged in session.exercises) {
                if (!logged.anchor) continue
                val exercise = catalog.find(logged.exerciseId) ?: continue
                val lock = AnchorLock(exercise.pattern, exercise.id, exercise.familyId)
                val choseThis = session.adjustments.any {
                    it.direction == AdjustmentDirection.CHOSEN && it.exerciseId == exercise.id
                }
                if (exercise.pattern !in found || choseThis) {
                    found[exercise.pattern] = lock
                }
            }
        }
        return found.values.toList()
    }

    fun effectiveTooHard(history: List<SessionRecord>, today: LocalDate): Boolean {
        val sessions = prior(history, today)
        val last = sessions.firstOrNull() ?: return false
        if (last.rating == SessionRating.TOO_HARD) return true
        val two = sessions.take(2)
        return two.size == 2 && two.all { it.rating == null }
    }

    fun loadAtCeiling(loadKg: Double?, profile: UserProfile): Boolean {
        val heaviest = profile.dumbbellKg.maxOrNull() ?: return true
        return loadKg != null && loadKg >= heaviest - 0.05
    }

    fun swapAllowed(
        familyId: String,
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
        today: LocalDate,
    ): Boolean {
        val last = prior(history, today).firstOrNull() ?: return false
        if (last.adjustments.any { it.familyId == familyId }) return true
        val memory = memories(catalog, listOf(last), today.plusDays(1)).firstOrNull { it.familyId == familyId }
        if (memory?.missed == true) return true
        val two = prior(history, today).take(2)
        if (two.size == 2 && two.all { it.rating == SessionRating.TOO_HARD }) return true
        if (last.rating == SessionRating.TOO_EASY && memory != null && loadAtCeiling(memory.loadKg, profile)) {
            return true
        }
        return false
    }

    fun stepFor(
        familyId: String,
        anchor: Boolean,
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
        today: LocalDate,
    ): Step {
        val last = prior(history, today).firstOrNull()
        val memories = memories(catalog, history, today)
        val memory = lastFamily(memories, familyId)
        val easier = last?.adjustments?.any { it.familyId == familyId && it.direction == AdjustmentDirection.EASIER } == true
        val harder = last?.adjustments?.any { it.familyId == familyId && it.direction == AdjustmentDirection.HARDER } == true
        val missed = memory != null && memory.date == last?.date && memory.missed
        val hit = memory != null && memory.date == last?.date && memory.hitTop
        val tooHard = effectiveTooHard(history, today)
        if (Training.isDeload(profile, today)) {
            return if (easier || missed || tooHard) Step.DOWN else Step.HOLD
        }
        if (tooHard) return if (easier || missed) Step.DOWN else Step.HOLD
        if (last != null && last.rating == null && !easier && !harder) return Step.HOLD
        if (easier || missed) return Step.DOWN
        if (harder) return Step.UP
        if (last?.rating == SessionRating.TOO_EASY && anchor) return Step.UP
        if (hit) return Step.UP
        return Step.HOLD
    }

    fun recentIds(history: List<SessionRecord>, today: LocalDate): Set<String> =
        prior(history, today).take(2).flatMap { session -> session.exercises.map { it.exerciseId } }.toSet()

    fun progressed(tier: Int, loadKg: Double?, memory: FamilyMemory?): Boolean {
        if (memory == null) return true
        if (tier > memory.tier) return true
        val previous = memory.loadKg
        return loadKg != null && previous != null && loadKg > previous + 0.05
    }

    fun notHarder(tier: Int, loadKg: Double?, memory: FamilyMemory): Boolean {
        if (tier > memory.tier) return false
        val previous = memory.loadKg
        if (loadKg != null && previous != null && loadKg > previous + 0.05) return false
        return true
    }

    fun maxed(exercise: ProgrammedExercise, loadKg: Double?, profile: UserProfile, catalog: Catalog): Boolean {
        val top = catalog.inFamily(exercise.familyId).maxOfOrNull { it.tier } ?: exercise.tier
        if (exercise.tier < top) return false
        if (exercise.loadType == LoadType.BODYWEIGHT) return true
        return loadAtCeiling(loadKg, profile)
    }

    fun sameLoad(left: Double?, right: Double?): Boolean = when {
        left == null && right == null -> true
        left == null || right == null -> false
        else -> abs(left - right) < 0.05
    }
}
