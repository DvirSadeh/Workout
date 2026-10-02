package workout.domain

import java.time.LocalDate
import kotlin.math.abs

object Checker {
    fun checkPlan(
        plan: SessionPlan,
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
        today: LocalDate,
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        val dose = Training.dose(profile, today)
        val focus = Training.focus(profile, history, today)
        val block = Training.blockIndex(profile, today)
        val slots = Training.slots(focus, dose.movementCount, block, profile.limits)
        val owned = Training.sortedOwned(profile)
        val memories = Signals.memories(catalog, history, today)
        val tooHard = Signals.effectiveTooHard(history, today)
        val last = Signals.prior(history, today).firstOrNull()

        if (plan.date != today) {
            violations += Violation("date", "Plan date does not match today.")
        }
        if (plan.focus != focus) {
            violations += Violation("focus", "Plan focus is ${plan.focus} and today calls for $focus.")
        }
        if (plan.deload != dose.deload) {
            violations += Violation("deload", "Deload flag does not match the training week.")
        }
        if (plan.note.length < 40) {
            violations += Violation("note", "Coach note must be at least two sentences about this user.")
        }
        if (plan.exercises.size !in (slots.size - 1)..(slots.size + 1)) {
            violations += Violation(
                "count",
                "Session has ${plan.exercises.size} exercises and this day needs about ${slots.size}.",
            )
        }
        if (plan.exercises.map { it.exerciseId }.distinct().size != plan.exercises.size) {
            violations += Violation("duplicate", "An exercise appears twice in the session.")
        }

        plan.exercises.forEach { planned ->
            val exercise = catalog.find(planned.exerciseId)
            if (exercise == null) {
                violations += Violation("unknown", "Unknown exercise ${planned.exerciseId}.")
                return@forEach
            }
            if (exercise.needsBench && !profile.hasBench) {
                violations += Violation("bench", "${exercise.name} needs a bench.")
            }
            if (exercise.excludedBy.any { it in profile.limits }) {
                violations += Violation("limit", "${exercise.name} is excluded by the user's limits.")
            }
            if (exercise.loadType == LoadType.BODYWEIGHT && planned.loadKg != null) {
                violations += Violation("load", "${exercise.name} is bodyweight and should not have a dumbbell load.")
            }
            if (exercise.loadType == LoadType.DUMBBELL) {
                val load = planned.loadKg
                if (load == null || owned.none { abs(it - load) < 0.05 }) {
                    violations += Violation("load", "${exercise.name} must use one of the owned dumbbells.")
                }
            }
            if (planned.sets != dose.sets) {
                violations += Violation("sets", "${exercise.name} should be ${dose.sets} sets.")
            }
            if (dose.deload) {
                if (planned.repsLow != dose.repsLow || planned.repsHigh != dose.repsLow) {
                    violations += Violation("reps", "${exercise.name} should stay at ${dose.repsLow} reps on a deload.")
                }
            } else if (planned.repsLow < dose.repsLow || planned.repsHigh > dose.repsHigh || planned.repsLow > planned.repsHigh) {
                violations += Violation("reps", "${exercise.name} reps sit outside ${dose.repsLow}-${dose.repsHigh}.")
            }
            if (planned.restSeconds < dose.restSeconds || planned.restSeconds > dose.restSeconds + 30) {
                violations += Violation("rest", "${exercise.name} rest is outside the goal range.")
            }
            checkStep(planned, exercise, memories, owned, profile, violations)
        }

        val assignment = assign(plan.exercises, slots, catalog)
        if (assignment == null) {
            violations += Violation("slots", "The exercises do not cover today's movement slots.")
        } else {
            checkLocks(assignment, slots, plan, catalog, profile, history, today, violations)
        }

        if (!tooHard) {
            val recent = Signals.recentIds(history, today)
            plan.exercises.filter { !it.anchor }.forEach { planned ->
                if (planned.exerciseId in recent) {
                    violations += Violation("repeat", "${planned.exerciseId} was already used in the last two sessions.")
                }
            }
        }

        if (tooHard) {
            plan.exercises.forEach { planned ->
                val exercise = catalog.find(planned.exerciseId) ?: return@forEach
                val memory = Signals.lastFamily(memories, exercise.familyId) ?: return@forEach
                if (!Signals.notHarder(exercise.tier, planned.loadKg, memory)) {
                    violations += Violation("too_hard", "${exercise.name} got harder after a session that was too hard.")
                }
            }
        }

        if (last?.rating == SessionRating.TOO_EASY && !tooHard) {
            val anchors = plan.exercises.filter { it.anchor }
            val anyProgress = anchors.any { planned ->
                val exercise = catalog.find(planned.exerciseId) ?: return@any false
                Signals.progressed(exercise.tier, planned.loadKg, Signals.lastFamily(memories, exercise.familyId))
            }
            val allMaxed = anchors.all { planned ->
                val exercise = catalog.find(planned.exerciseId) ?: return@all true
                Signals.maxed(exercise, planned.loadKg, profile, catalog)
            }
            if (!anyProgress && !allMaxed) {
                violations += Violation("too_easy", "The last session was too easy and no main lift progressed.")
            }
        }

        last?.adjustments?.forEach { adjustment ->
            val planned = plan.exercises.firstNotNullOfOrNull { item ->
                val exercise = catalog.find(item.exerciseId) ?: return@firstNotNullOfOrNull null
                if (exercise.familyId == adjustment.familyId) exercise to item else null
            }
            val memory = Signals.lastFamily(memories, adjustment.familyId)
            if (planned == null || memory == null) return@forEach
            val (exercise, item) = planned
            if (adjustment.direction == AdjustmentDirection.EASIER &&
                !Signals.notHarder(exercise.tier, item.loadKg, memory)
            ) {
                violations += Violation("easier", "${exercise.name} got harder after the user marked that movement easier.")
            }
            if (adjustment.direction == AdjustmentDirection.HARDER && !tooHard &&
                !Signals.progressed(exercise.tier, item.loadKg, memory) &&
                !Signals.maxed(exercise, item.loadKg, profile, catalog)
            ) {
                violations += Violation("harder", "${exercise.name} did not progress after the user marked it harder.")
            }
        }

        return violations
    }

    private fun checkStep(
        planned: PlannedExercise,
        exercise: ProgrammedExercise,
        memories: List<FamilyMemory>,
        owned: List<Double>,
        profile: UserProfile,
        violations: MutableList<Violation>,
    ) {
        val previousExercise = Signals.lastUse(memories, exercise.id)
        val previousFamily = Signals.lastFamily(memories, exercise.familyId)
        if (previousExercise?.loadKg != null && planned.loadKg != null && owned.isNotEmpty()) {
            val from = Training.loadIndex(owned, previousExercise.loadKg)
            val to = Training.loadIndex(owned, planned.loadKg)
            if (abs(from - to) > 1) {
                violations += Violation("step", "${exercise.name} changed the dumbbell by more than one step.")
            }
        }
        val continuingFamilyLoad = previousExercise == null &&
            previousFamily?.loadKg != null &&
            planned.loadKg != null &&
            planned.loadKg <= previousFamily.loadKg + 0.05
        if (previousExercise == null && planned.loadKg != null && !continuingFamilyLoad) {
            val guess = Training.initialLoad(exercise.pattern, profile)
            if (guess != null && owned.isNotEmpty()) {
                val cap = Training.loadIndex(owned, guess) + 1
                if (Training.loadIndex(owned, planned.loadKg) > cap) {
                    violations += Violation("start", "${exercise.name} starts heavier than a conservative guess.")
                }
            }
        }
        if (previousFamily != null && abs(exercise.tier - previousFamily.tier) > 1) {
            violations += Violation("tier", "${exercise.name} jumped more than one difficulty step.")
        }
        if (previousFamily != null && exercise.tier > previousFamily.tier) {
            val previousLoad = previousFamily.loadKg
            val nextLoad = planned.loadKg
            if (previousLoad != null && nextLoad != null && nextLoad > previousLoad + 0.05) {
                violations += Violation("both", "${exercise.name} went up a tier and up in weight in the same session.")
            }
        }
    }

    private fun checkLocks(
        assignment: List<Int>,
        slots: List<Slot>,
        plan: SessionPlan,
        catalog: Catalog,
        profile: UserProfile,
        history: List<SessionRecord>,
        today: LocalDate,
        violations: MutableList<Violation>,
    ) {
        val locks = Signals.locks(profile, catalog, history, today)
        assignment.forEachIndexed { slotIndex, exerciseIndex ->
            val slot = slots[slotIndex]
            if (!slot.anchor) return@forEachIndexed
            val planned = plan.exercises[exerciseIndex]
            val exercise = catalog.find(planned.exerciseId) ?: return@forEachIndexed
            val lock = locks.find { it.pattern == exercise.pattern } ?: return@forEachIndexed
            if (lock.exerciseId != exercise.id &&
                !Signals.swapAllowed(lock.familyId, profile, catalog, history, today)
            ) {
                violations += Violation("anchor", "${exercise.name} replaces the locked main lift ${lock.exerciseId}.")
            }
        }
    }

    private fun assign(exercises: List<PlannedExercise>, slots: List<Slot>, catalog: Catalog): List<Int>? {
        if (exercises.size !in (slots.size - 1)..(slots.size + 1)) return null
        if (exercises.size == slots.size) return solve(exercises, slots, catalog)
        if (exercises.size == slots.size - 1) return solve(exercises, slots.dropLast(1), catalog)
        val extras = exercises.indices.filter { !exercises[it].anchor }
        for (skip in extras) {
            val reduced = exercises.filterIndexed { index, _ -> index != skip }
            val solved = solve(reduced, slots, catalog) ?: continue
            return solved.map { original -> if (original >= skip) original + 1 else original }
        }
        return null
    }

    private fun solve(exercises: List<PlannedExercise>, slots: List<Slot>, catalog: Catalog): List<Int>? {
        val used = BooleanArray(exercises.size)
        val result = IntArray(slots.size)
        fun visit(slotIndex: Int): Boolean {
            if (slotIndex == slots.size) return true
            val slot = slots[slotIndex]
            for (index in exercises.indices) {
                if (used[index]) continue
                val planned = exercises[index]
                val exercise = catalog.find(planned.exerciseId) ?: continue
                if (planned.anchor != slot.anchor) continue
                if (exercise.pattern !in slot.patterns) continue
                used[index] = true
                result[slotIndex] = index
                if (visit(slotIndex + 1)) return true
                used[index] = false
            }
            return false
        }
        return if (visit(0)) result.toList() else null
    }
}
