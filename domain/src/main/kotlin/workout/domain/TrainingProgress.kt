package workout.domain

import java.time.LocalDate

data class ProgressPoint(val date: LocalDate, val level: Double)

data class MuscleLine(val muscle: String, val points: List<ProgressPoint>)

object TrainingProgress {
    private val order = listOf(
        "chest",
        "shoulders",
        "middle back",
        "biceps",
        "triceps",
        "abdominals",
        "lower back",
        "glutes",
        "quadriceps",
        "hamstrings",
        "calves",
    )

    /**
     * One line per muscle group. A set's work is kilograms times reps, or just reps
     * when the set has no dumbbell. An exercise adds that work to each primary muscle.
     * The group's first workout is level 1. A later workout is that day's work divided
     * by the first, so a higher point is more work than where the group started.
     */
    fun lines(
        sessions: List<SessionRecord>,
        musclesOf: (String) -> List<String>,
    ): List<MuscleLine> {
        val byMuscle = linkedMapOf<String, MutableList<Pair<LocalDate, Double>>>()
        sessions.sortedBy { it.date }.forEach { session ->
            val totals = linkedMapOf<String, Double>()
            session.exercises.forEach { logged ->
                val work = logged.sets.sumOf { setWork(it) }
                if (work <= 0.0) return@forEach
                musclesOf(logged.exerciseId)
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .forEach { muscle ->
                        totals[muscle] = (totals[muscle] ?: 0.0) + work
                    }
            }
            totals.forEach { (muscle, work) ->
                byMuscle.getOrPut(muscle) { mutableListOf() }.add(session.date to work)
            }
        }
        return byMuscle
            .map { (muscle, series) ->
                val baseline = series.first().second
                MuscleLine(
                    muscle = muscle,
                    points = series.map { (date, work) ->
                        ProgressPoint(date, if (baseline <= 0.0) 1.0 else work / baseline)
                    },
                )
            }
            .sortedWith(compareBy({ rank(it.muscle) }, { it.muscle }))
    }

    fun setWork(set: LoggedSet): Double {
        if (!set.completed || set.reps <= 0) return 0.0
        val load = set.loadKg ?: 0.0
        return if (load > 0.0) load * set.reps else set.reps.toDouble()
    }

    private fun rank(muscle: String): Int {
        val index = order.indexOf(muscle)
        return if (index < 0) order.size else index
    }
}
