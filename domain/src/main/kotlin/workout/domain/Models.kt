package workout.domain

import java.time.LocalDate

enum class Goal { FIT, FAT_LOSS, MUSCLE, STRENGTH }

enum class Experience { NEW, SOME, REGULAR }

enum class Sex { FEMALE, MALE, UNSPECIFIED }

enum class LimitTag { KNEES, SHOULDERS, LOWER_BACK, WRISTS }

enum class MovementPattern {
    SQUAT,
    HINGE,
    LUNGE,
    HORIZONTAL_PUSH,
    VERTICAL_PUSH,
    HORIZONTAL_PULL,
    CORE,
    ISOLATION,
}

enum class LoadType { DUMBBELL, BODYWEIGHT }

enum class SessionRating { TOO_EASY, JUST_RIGHT, HARD_BUT_GOOD, TOO_HARD }

enum class AdjustmentDirection { EASIER, HARDER }

enum class PlanSource { COACH, FALLBACK }

enum class DayFocus { FULL_BODY, UPPER, LOWER }

enum class Step { DOWN, HOLD, UP }

data class UserProfile(
    val birthYear: Int,
    val sex: Sex,
    val heightCm: Int,
    val weightKg: Double,
    val experience: Experience,
    val goal: Goal,
    val daysPerWeek: Int,
    val minutesPerSession: Int,
    val dumbbellKg: List<Double>,
    val hasBench: Boolean,
    val limits: Set<LimitTag>,
    val trainingStart: LocalDate,
)

data class CuratedEntry(
    val id: String,
    val pattern: MovementPattern,
    val loadType: LoadType,
    val needsBench: Boolean,
    val familyId: String,
    val tier: Int,
    val excludedBy: Set<LimitTag> = emptySet(),
)

data class ExerciseText(
    val name: String,
    val instructions: List<String>,
    val primaryMuscles: List<String>,
    val imageFiles: List<String>,
)

data class ProgrammedExercise(
    val id: String,
    val name: String,
    val instructions: List<String>,
    val primaryMuscles: List<String>,
    val imageFiles: List<String>,
    val pattern: MovementPattern,
    val loadType: LoadType,
    val needsBench: Boolean,
    val familyId: String,
    val tier: Int,
    val excludedBy: Set<LimitTag>,
)

data class Catalog(val exercises: List<ProgrammedExercise>) {
    private val byId = exercises.associateBy { it.id }

    fun find(id: String): ProgrammedExercise? = byId[id]

    fun require(id: String): ProgrammedExercise = byId[id] ?: error("Unknown exercise $id")

    fun inFamily(familyId: String): List<ProgrammedExercise> =
        exercises.filter { it.familyId == familyId }.sortedBy { it.tier }

    fun eligible(profile: UserProfile): List<ProgrammedExercise> = exercises.filter { exercise ->
        if (exercise.needsBench && !profile.hasBench) return@filter false
        if (exercise.excludedBy.any { it in profile.limits }) return@filter false
        if (exercise.loadType == LoadType.DUMBBELL && profile.dumbbellKg.isEmpty()) return@filter false
        true
    }
}

data class LoggedSet(
    val reps: Int,
    val loadKg: Double?,
    val completed: Boolean,
)

data class LoggedExercise(
    val exerciseId: String,
    val prescribedSets: Int,
    val repsLow: Int,
    val repsHigh: Int,
    val prescribedLoadKg: Double?,
    val sets: List<LoggedSet>,
    val anchor: Boolean,
) {
    fun hitTop(): Boolean {
        val done = sets.filter { it.completed }
        return done.size >= prescribedSets && done.all { it.reps >= repsHigh }
    }

    fun missed(): Boolean = sets.any { it.completed && it.reps < repsLow }
}

data class AdjustmentRecord(
    val exerciseId: String,
    val familyId: String,
    val direction: AdjustmentDirection,
)

data class SessionRecord(
    val date: LocalDate,
    val rating: SessionRating?,
    val source: PlanSource,
    val note: String,
    val focus: DayFocus,
    val exercises: List<LoggedExercise>,
    val adjustments: List<AdjustmentRecord>,
)

data class PlannedExercise(
    val exerciseId: String,
    val sets: Int,
    val repsLow: Int,
    val repsHigh: Int,
    val loadKg: Double?,
    val restSeconds: Int,
    val reason: String,
    val anchor: Boolean,
)

data class SessionPlan(
    val date: LocalDate,
    val note: String,
    val exercises: List<PlannedExercise>,
    val source: PlanSource,
    val blockIndex: Int,
    val weekInBlock: Int,
    val focus: DayFocus,
    val deload: Boolean,
)

data class Violation(val code: String, val message: String)

data class Slot(val patterns: Set<MovementPattern>, val anchor: Boolean)

data class Dose(
    val repsLow: Int,
    val repsHigh: Int,
    val restSeconds: Int,
    val movementCount: Int,
    val sets: Int,
    val deload: Boolean,
)

data class AdjustmentResult(
    val plan: SessionPlan,
    val adjustments: List<AdjustmentRecord>,
    val message: String,
)

data class CoachRequest(
    val systemPrompt: String,
    val payloadJson: String,
)
