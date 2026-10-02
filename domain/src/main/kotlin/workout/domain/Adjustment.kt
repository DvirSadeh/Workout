package workout.domain

import kotlin.math.abs

object Adjustments {
    fun apply(
        plan: SessionPlan,
        catalog: Catalog,
        profile: UserProfile,
        exerciseId: String,
        direction: AdjustmentDirection,
        startedExerciseIds: Set<String> = emptySet(),
    ): AdjustmentResult {
        val index = plan.exercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index < 0) {
            return AdjustmentResult(plan, emptyList(), "That exercise is not in today's session.")
        }
        val current = catalog.find(exerciseId)
            ?: return AdjustmentResult(plan, emptyList(), "That exercise is not in the catalog.")
        val targets = if (direction == AdjustmentDirection.HARDER) {
            listOf(index)
        } else {
            plan.exercises.indices.filter { item ->
                val exercise = catalog.find(plan.exercises[item].exerciseId) ?: return@filter false
                val later = item > index &&
                    plan.exercises[item].exerciseId !in startedExerciseIds &&
                    exercise.pattern == current.pattern
                item == index || later
            }
        }
        val updated = plan.exercises.toMutableList()
        val records = mutableListOf<AdjustmentRecord>()
        val names = mutableListOf<String>()
        for (target in targets) {
            val planned = updated[target]
            val exercise = catalog.find(planned.exerciseId) ?: continue
            val blocked = updated.map { it.exerciseId }.toSet() - planned.exerciseId
            val shifted = shift(exercise, planned, direction, catalog, profile, blocked)
            updated[target] = shifted
            records += AdjustmentRecord(shifted.exerciseId, exercise.familyId, direction)
            names += catalog.find(shifted.exerciseId)?.name ?: exercise.name
        }
        val word = if (direction == AdjustmentDirection.EASIER) "easier" else "harder"
        val message = "Made ${names.distinct().joinToString(" and ")} a step $word."
        return AdjustmentResult(plan.copy(exercises = updated), records, message)
    }

    private fun shift(
        exercise: ProgrammedExercise,
        planned: PlannedExercise,
        direction: AdjustmentDirection,
        catalog: Catalog,
        profile: UserProfile,
        blockedIds: Set<String>,
    ): PlannedExercise {
        val owned = Training.sortedOwned(profile)
        val family = catalog.inFamily(exercise.familyId).filter { candidate ->
            if (candidate.id in blockedIds) return@filter false
            if (candidate.needsBench && !profile.hasBench) return@filter false
            if (candidate.excludedBy.any { it in profile.limits }) return@filter false
            true
        }
        val load = planned.loadKg
        if (direction == AdjustmentDirection.EASIER) {
            if (exercise.loadType == LoadType.DUMBBELL && load != null) {
                val lighter = Training.lighter(owned, load)
                if (lighter != null) return planned.copy(loadKg = lighter, repsHigh = planned.repsLow)
            }
            val lower = family.filter { it.tier < exercise.tier }.maxByOrNull { it.tier }
            if (lower != null) return retarget(planned, lower, profile, lighter = true)
        } else {
            if (exercise.loadType == LoadType.DUMBBELL && load != null) {
                val heavier = Training.heavier(owned, load)
                if (heavier != null) return planned.copy(loadKg = heavier, repsHigh = planned.repsLow)
            }
            val higher = family.filter { it.tier > exercise.tier }.minByOrNull { it.tier }
            if (higher != null) return retarget(planned, higher, profile, lighter = false)
        }
        return planned.copy(repsHigh = planned.repsLow)
    }

    private fun retarget(
        planned: PlannedExercise,
        exercise: ProgrammedExercise,
        profile: UserProfile,
        lighter: Boolean,
    ): PlannedExercise {
        val load = when (exercise.loadType) {
            LoadType.BODYWEIGHT -> null
            LoadType.DUMBBELL -> {
                val owned = Training.sortedOwned(profile)
                val current = planned.loadKg
                when {
                    current == null -> Training.initialLoad(exercise.pattern, profile) ?: owned.firstOrNull()
                    lighter -> Training.lighter(owned, current) ?: Training.roundDown(owned, current) ?: owned.firstOrNull()
                    else -> Training.heavier(owned, current) ?: current
                }
            }
        }
        return planned.copy(
            exerciseId = exercise.id,
            loadKg = load,
            repsHigh = planned.repsLow,
        )
    }

    fun adopt(
        plan: SessionPlan,
        catalog: Catalog,
        profile: UserProfile,
        chosenExerciseId: String,
        replacingExerciseId: String? = null,
    ): AdjustmentResult {
        val chosen = catalog.find(chosenExerciseId)
            ?: return AdjustmentResult(plan, emptyList(), "That exercise is not in the catalog.")
        if (plan.exercises.any { it.exerciseId == chosen.id }) {
            return AdjustmentResult(plan, emptyList(), "${chosen.name} is already in today's workout.")
        }
        if (chosen.needsBench && !profile.hasBench) {
            return AdjustmentResult(plan, emptyList(), "${chosen.name} needs a bench.")
        }
        if (chosen.excludedBy.any { it in profile.limits }) {
            return AdjustmentResult(plan, emptyList(), "${chosen.name} is turned off for this plan.")
        }
        if (chosen.loadType == LoadType.DUMBBELL && profile.dumbbellKg.isEmpty()) {
            return AdjustmentResult(plan, emptyList(), "${chosen.name} needs a dumbbell.")
        }
        val candidates = plan.exercises.indices.filter { index ->
            catalog.find(plan.exercises[index].exerciseId)?.familyId == chosen.familyId
        }
        if (candidates.isEmpty()) {
            return AdjustmentResult(plan, emptyList(), "That movement is not in today's workout.")
        }
        val framed = candidates.firstOrNull { plan.exercises[it].exerciseId == replacingExerciseId }
        val target = framed ?: candidates.minWith(
            compareBy(
                { if (plan.exercises[it].anchor) 0 else 1 },
                { abs((catalog.find(plan.exercises[it].exerciseId)?.tier ?: 0) - chosen.tier) },
                { it },
            ),
        )
        val current = plan.exercises[target]
        val updated = current.copy(
            exerciseId = chosen.id,
            loadKg = loadForVersion(current, chosen, profile),
            reason = "You chose this version for today.",
        )
        val exercises = plan.exercises.toMutableList()
        exercises[target] = updated
        return AdjustmentResult(
            plan.copy(exercises = exercises),
            listOf(AdjustmentRecord(chosen.id, chosen.familyId, AdjustmentDirection.CHOSEN)),
            "Using ${chosen.name} today.",
        )
    }

    private fun loadForVersion(
        planned: PlannedExercise,
        chosen: ProgrammedExercise,
        profile: UserProfile,
    ): Double? {
        if (chosen.loadType == LoadType.BODYWEIGHT) return null
        val owned = Training.sortedOwned(profile)
        if (owned.isEmpty()) return null
        val current = planned.loadKg
        return if (current == null) {
            Training.initialLoad(chosen.pattern, profile) ?: owned.first()
        } else {
            Training.roundDown(owned, current) ?: owned.first()
        }
    }
}
