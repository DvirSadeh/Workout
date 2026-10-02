package workout.domain

import java.time.LocalDate

object WorkoutSize {
    fun range(focus: DayFocus): IntRange = when (focus) {
        DayFocus.FULL_BODY -> 3..7
        DayFocus.UPPER, DayFocus.LOWER -> 2..7
    }

    fun resize(
        plan: SessionPlan,
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
        delta: Int,
    ): SessionPlan {
        if (delta == 0 || plan.exercises.isEmpty()) return plan
        val target = (plan.exercises.size + delta).coerceIn(range(plan.focus))
        if (target == plan.exercises.size) return plan
        if (target < plan.exercises.size) {
            return plan.copy(exercises = plan.exercises.take(target))
        }
        var current = plan
        while (current.exercises.size < target) {
            val grown = addOne(current, profile, catalog, history) ?: return current
            current = grown
        }
        return current
    }

    fun remove(plan: SessionPlan, exerciseId: String): SessionPlan {
        val index = plan.exercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index < 0 || plan.exercises.size <= range(plan.focus).first) return plan
        return plan.copy(exercises = plan.exercises.filterIndexed { i, _ -> i != index })
    }

    fun reorder(plan: SessionPlan, idsInOrder: List<String>): SessionPlan {
        val current = plan.exercises.map { it.exerciseId }
        if (idsInOrder.size != current.size || idsInOrder.toSet() != current.toSet()) return plan
        val byId = plan.exercises.associateBy { it.exerciseId }
        val next = idsInOrder.map { byId.getValue(it) }
        if (next == plan.exercises) return plan
        return plan.copy(exercises = next)
    }

    private fun addOne(
        plan: SessionPlan,
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
    ): SessionPlan? {
        val nextCount = plan.exercises.size + 1
        val slot = Training.slots(plan.focus, nextCount, plan.blockIndex, profile.limits).getOrNull(plan.exercises.size)
            ?: return null
        val used = plan.exercises.map { it.exerciseId }.toSet()
        val pool = catalog.eligible(profile).filter { it.pattern in slot.patterns && it.id !in used }
        if (pool.isEmpty()) return null
        val memories = Signals.memories(catalog, history, plan.date)
        val banned = Signals.recentIds(history, plan.date)
        val tooHard = Signals.effectiveTooHard(history, plan.date)
        val tierSafe = if (tooHard) {
            pool.filter { exercise ->
                val previous = Signals.lastFamily(memories, exercise.familyId)
                previous == null || exercise.tier <= previous.tier
            }.ifEmpty { pool }
        } else {
            pool
        }
        val choices = tierSafe.filter { it.id !in banned }.ifEmpty { tierSafe }
        val chosen = choices.minWith(
            compareBy(
                { Signals.lastUse(memories, it.id)?.date ?: LocalDate.MIN },
                { it.tier },
                { it.id },
            ),
        )
        val template = plan.exercises.maxBy { it.repsHigh - it.repsLow }
        val added = PlannedExercise(
            exerciseId = chosen.id,
            sets = template.sets,
            repsLow = template.repsLow,
            repsHigh = template.repsHigh,
            loadKg = loadFor(chosen, profile),
            restSeconds = template.restSeconds,
            reason = "Added so today's workout is longer.",
            anchor = slot.anchor,
        )
        return plan.copy(exercises = plan.exercises + added)
    }

    private fun loadFor(exercise: ProgrammedExercise, profile: UserProfile): Double? {
        if (exercise.loadType == LoadType.BODYWEIGHT) return null
        return Training.initialLoad(exercise.pattern, profile) ?: Training.sortedOwned(profile).firstOrNull()
    }
}
