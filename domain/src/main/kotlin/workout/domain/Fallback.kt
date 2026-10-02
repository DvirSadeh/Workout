package workout.domain

import java.time.LocalDate

object Fallback {
    fun plan(
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
        today: LocalDate,
    ): SessionPlan {
        val dose = Training.dose(profile, today)
        val focus = Training.focus(profile, history, today)
        val block = Training.blockIndex(profile, today)
        val week = Training.weekInBlock(profile, today)
        val slots = Training.slots(focus, dose.movementCount, block, profile.limits)
        val eligible = catalog.eligible(profile)
        val banned = Signals.recentIds(history, today)
        val locks = Signals.locks(profile, catalog, history, today)
        val memories = Signals.memories(catalog, history, today)
        val used = mutableSetOf<String>()
        val chosen = mutableListOf<Pair<ProgrammedExercise, PlannedExercise>>()

        for (slot in slots) {
            val pool = eligible.filter { it.pattern in slot.patterns && it.id !in used }
            val exercise = pick(slot, pool, banned, locks, memories, profile, catalog, history, today)
            val step = Signals.stepFor(exercise.familyId, slot.anchor, profile, catalog, history, today)
            val load = loadFor(exercise, step, memories, profile)
            val repsLow = dose.repsLow
            val repsHigh = if (dose.deload) dose.repsLow else dose.repsHigh
            val reason = if (slot.anchor) {
                "Main lift for this block."
            } else {
                "Extra chosen so it was not used in the last two sessions."
            }
            chosen += exercise to PlannedExercise(
                exerciseId = exercise.id,
                sets = dose.sets,
                repsLow = repsLow,
                repsHigh = repsHigh,
                loadKg = load,
                restSeconds = dose.restSeconds,
                reason = reason,
                anchor = slot.anchor,
            )
            used += exercise.id
        }

        return SessionPlan(
            date = today,
            note = note(chosen.map { it.second }, catalog, history, today, dose.deload),
            exercises = chosen.map { it.second },
            source = PlanSource.FALLBACK,
            blockIndex = block,
            weekInBlock = week,
            focus = focus,
            deload = dose.deload,
        )
    }

    private fun pick(
        slot: Slot,
        pool: List<ProgrammedExercise>,
        banned: Set<String>,
        locks: List<AnchorLock>,
        memories: List<FamilyMemory>,
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
        today: LocalDate,
    ): ProgrammedExercise {
        require(pool.isNotEmpty()) { "No exercise for $slot" }
        if (slot.anchor) {
            val pattern = slot.patterns.first()
            val lock = locks.find { it.pattern == pattern }
            val locked = lock?.let { item -> pool.find { it.id == item.exerciseId } }
            if (locked != null && !Signals.swapAllowed(locked.familyId, profile, catalog, history, today)) {
                return locked
            }
            val family = Training.mainFamily(pattern)
            val familyPool = if (family == null) pool else pool.filter { it.familyId == family }.ifEmpty { pool }
            val previous = family?.let { Signals.lastFamily(memories, it) }
            val step = if (previous == null) {
                Step.HOLD
            } else {
                Signals.stepFor(previous.familyId, true, profile, catalog, history, today)
            }
            val base = previous?.tier ?: Training.introTier(profile, today, familyPool.maxOf { it.tier } + 1)
            val desired = when (step) {
                Step.UP -> base + 1
                Step.DOWN -> base - 1
                Step.HOLD -> base
            }
            val maxTier = familyPool.maxOf { it.tier }
            val target = desired.coerceIn(0, maxTier)
            if (locked != null && !canChangeTier(locked, target, step, memories, profile)) {
                return locked
            }
            return familyPool.minWith(compareBy({ kotlin.math.abs(it.tier - target) }, { it.tier }, { it.id }))
        }
        val tooHard = Signals.effectiveTooHard(history, today)
        val tierSafe = if (tooHard) {
            pool.filter { exercise ->
                val previous = Signals.lastFamily(memories, exercise.familyId)
                previous == null || exercise.tier <= previous.tier
            }.ifEmpty { pool }
        } else {
            pool
        }
        val fresh = tierSafe.filter { it.id !in banned }
        val choices = fresh.ifEmpty {
            require(tooHard) { "No fresh extra for $slot from ${pool.map { it.id }}" }
            tierSafe
        }
        return choices.minWith(
            compareBy(
                { Signals.lastUse(memories, it.id)?.date ?: LocalDate.MIN },
                { it.tier },
                { it.id },
            ),
        )
    }

    private fun canChangeTier(
        locked: ProgrammedExercise,
        targetTier: Int,
        step: Step,
        memories: List<FamilyMemory>,
        profile: UserProfile,
    ): Boolean {
        if (targetTier == locked.tier) return false
        if (step == Step.HOLD) return false
        val previous = Signals.lastUse(memories, locked.id)
        val load = previous?.loadKg
        if (step == Step.UP && load != null && Training.heavier(profile.dumbbellKg, load) != null) {
            return false
        }
        if (step == Step.DOWN && load != null && Training.lighter(profile.dumbbellKg, load) != null) {
            return false
        }
        return true
    }

    private fun loadFor(
        exercise: ProgrammedExercise,
        step: Step,
        memories: List<FamilyMemory>,
        profile: UserProfile,
    ): Double? {
        if (exercise.loadType == LoadType.BODYWEIGHT) return null
        val owned = Training.sortedOwned(profile)
        if (owned.isEmpty()) return null
        val previousExercise = Signals.lastUse(memories, exercise.id)
        val previousFamily = Signals.lastFamily(memories, exercise.familyId)
        if (previousExercise?.loadKg == null) {
            val guess = Training.initialLoad(exercise.pattern, profile) ?: owned.first()
            val familyLoad = previousFamily?.loadKg
            if (familyLoad != null && exercise.tier > (previousFamily.tier)) {
                return Training.roundDown(owned, familyLoad) ?: owned.first()
            }
            return guess
        }
        val current = Training.nearest(owned, previousExercise.loadKg)
        return when (step) {
            Step.UP -> Training.heavier(owned, current) ?: current
            Step.DOWN -> Training.lighter(owned, current) ?: current
            Step.HOLD -> current
        }
    }

    private fun note(
        exercises: List<PlannedExercise>,
        catalog: Catalog,
        history: List<SessionRecord>,
        today: LocalDate,
        deload: Boolean,
    ): String {
        val last = Signals.prior(history, today).firstOrNull()
        val deloadLine = if (deload) " This is a lighter deload week." else ""
        if (last == null) {
            val sample = exercises.firstOrNull { it.loadKg != null }
            val name = sample?.let { catalog.find(it.exerciseId)?.name } ?: "The first dumbbell lift"
            val load = sample?.loadKg?.let { formatKg(it) } ?: "a light"
            return "This first session finds a starting weight. $name begins at $load kg, a light guess from your body weight. Use Easier or Harder if that guess is off.$deloadLine"
        }
        val rated = when (last.rating) {
            SessionRating.TOO_EASY -> "too easy"
            SessionRating.JUST_RIGHT -> "just right"
            SessionRating.HARD_BUT_GOOD -> "hard but good"
            SessionRating.TOO_HARD -> "too hard"
            null -> "unfinished"
        }
        val logged = last.exercises.firstOrNull { it.prescribedLoadKg != null }
        val name = logged?.let { catalog.find(it.exerciseId)?.name } ?: "the last main lift"
        val load = logged?.prescribedLoadKg?.let { formatKg(it) } ?: "bodyweight"
        return "Last time $name was $load kg and the session felt $rated. Today keeps the main lifts and changes the extras.$deloadLine"
    }

    private fun formatKg(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
