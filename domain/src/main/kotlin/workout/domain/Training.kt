package workout.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object Training {
    fun age(profile: UserProfile, today: LocalDate): Int =
        (today.year - profile.birthYear).coerceAtLeast(0)

    fun daysSinceStart(profile: UserProfile, today: LocalDate): Int =
        ChronoUnit.DAYS.between(profile.trainingStart, today).toInt().coerceAtLeast(0)

    fun blockIndex(profile: UserProfile, today: LocalDate): Int = daysSinceStart(profile, today) / 28

    fun weekInBlock(profile: UserProfile, today: LocalDate): Int = (daysSinceStart(profile, today) % 28) / 7

    fun isDeload(profile: UserProfile, today: LocalDate): Boolean = weekInBlock(profile, today) == 3

    fun focus(profile: UserProfile, history: List<SessionRecord>, today: LocalDate): DayFocus {
        if (profile.daysPerWeek <= 3) return DayFocus.FULL_BODY
        val prior = history.filter { it.date < today }.maxByOrNull { it.date }
        return if (prior == null || prior.focus == DayFocus.LOWER) DayFocus.UPPER else DayFocus.LOWER
    }

    fun dose(profile: UserProfile, today: LocalDate): Dose {
        val (low, high, restBase) = when (profile.goal) {
            Goal.FIT -> Triple(8, 15, 60)
            Goal.FAT_LOSS -> Triple(8, 15, 45)
            Goal.MUSCLE -> Triple(8, 12, 75)
            Goal.STRENGTH -> Triple(6, 10, 90)
        }
        val count = when {
            profile.minutesPerSession <= 20 -> 4
            profile.minutesPerSession <= 30 -> 5
            profile.minutesPerSession <= 45 -> 6
            else -> 7
        }
        var sets = when {
            profile.minutesPerSession <= 20 -> 2
            profile.minutesPerSession >= 60 -> 4
            else -> 3
        }
        val age = age(profile, today)
        if (age >= 60 && profile.experience == Experience.NEW) sets -= 1
        val deload = isDeload(profile, today)
        if (deload) sets -= 1
        val rest = if (age >= 55) restBase + 15 else restBase
        return Dose(low, high, rest, count, sets.coerceAtLeast(1), deload)
    }

    fun slots(focus: DayFocus, count: Int, blockIndex: Int, limits: Set<LimitTag>): List<Slot> {
        val lower = if (blockIndex % 2 == 0) MovementPattern.SQUAT else MovementPattern.HINGE
        val other = if (lower == MovementPattern.SQUAT) MovementPattern.HINGE else MovementPattern.SQUAT
        val shoulders = LimitTag.SHOULDERS in limits
        val knees = LimitTag.KNEES in limits
        val built = when (focus) {
            DayFocus.FULL_BODY -> fullBodySlots(count, lower, other, shoulders, knees)
            DayFocus.UPPER -> upperSlots(count, shoulders)
            DayFocus.LOWER -> lowerSlots(count, lower, other, knees, shoulders)
        }
        return built.take(count)
    }

    fun introTier(profile: UserProfile, today: LocalDate, familySize: Int): Int {
        if (familySize <= 0) return 0
        val tier = when (profile.experience) {
            Experience.NEW -> 0
            Experience.SOME -> 1
            Experience.REGULAR -> 2
        }
        return tier.coerceAtMost(familySize - 1)
    }

    fun introTierCap(profile: UserProfile, today: LocalDate, familySize: Int): Int {
        val intro = introTier(profile, today, familySize)
        val age = age(profile, today)
        return if (age >= 60 && profile.experience == Experience.NEW) {
            intro
        } else {
            (intro + 1).coerceAtMost(familySize - 1)
        }
    }

    fun mainFamily(pattern: MovementPattern): String? = when (pattern) {
        MovementPattern.SQUAT -> "squat"
        MovementPattern.HINGE -> "hinge"
        MovementPattern.LUNGE -> "lunge"
        MovementPattern.HORIZONTAL_PUSH -> "horizontal_push"
        MovementPattern.HORIZONTAL_PULL -> "row"
        MovementPattern.VERTICAL_PUSH -> "vertical_push"
        MovementPattern.CORE -> "core"
        MovementPattern.ISOLATION -> null
    }

    fun initialLoad(pattern: MovementPattern, profile: UserProfile): Double? {
        val base = when (pattern) {
            MovementPattern.SQUAT, MovementPattern.LUNGE -> 0.15
            MovementPattern.HINGE -> 0.18
            MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PULL -> 0.12
            MovementPattern.VERTICAL_PUSH -> 0.08
            MovementPattern.ISOLATION -> 0.05
            MovementPattern.CORE -> return null
        }
        val sexScale = if (profile.sex == Sex.MALE) 1.0 else 0.7
        val experienceScale = when (profile.experience) {
            Experience.NEW -> 0.55
            Experience.SOME -> 0.8
            Experience.REGULAR -> 1.0
        }
        val target = profile.weightKg * base * sexScale * experienceScale
        return roundDown(profile.dumbbellKg, target) ?: profile.dumbbellKg.minOrNull()
    }

    fun roundDown(owned: List<Double>, target: Double): Double? =
        owned.filter { it <= target + 0.05 }.maxOrNull()

    fun nearest(owned: List<Double>, load: Double): Double =
        owned.minBy { abs(it - load) }

    fun heavier(owned: List<Double>, load: Double): Double? =
        owned.filter { it > load + 0.05 }.minOrNull()

    fun lighter(owned: List<Double>, load: Double): Double? =
        owned.filter { it < load - 0.05 }.maxOrNull()

    fun loadIndex(owned: List<Double>, load: Double): Int {
        val sorted = owned.sorted()
        return sorted.indices.minBy { abs(sorted[it] - load) }
    }

    fun sortedOwned(profile: UserProfile): List<Double> = profile.dumbbellKg.distinct().sorted()

    private fun fullBodySlots(
        count: Int,
        lower: MovementPattern,
        other: MovementPattern,
        shoulders: Boolean,
        knees: Boolean,
    ): List<Slot> = buildList {
        add(Slot(setOf(lower), true))
        add(Slot(setOf(MovementPattern.HORIZONTAL_PUSH), true))
        add(Slot(setOf(MovementPattern.HORIZONTAL_PULL), true))
        val fourth = if (knees) setOf(other) else setOf(other, MovementPattern.LUNGE)
        add(Slot(fourth, false))
        if (count >= 5) add(Slot(setOf(MovementPattern.CORE), false))
        if (count >= 6) {
            add(
                Slot(
                    if (shoulders) setOf(MovementPattern.ISOLATION) else setOf(MovementPattern.VERTICAL_PUSH),
                    false,
                ),
            )
        }
        if (count >= 7) {
            add(Slot(if (shoulders) setOf(MovementPattern.CORE) else setOf(MovementPattern.ISOLATION), false))
        }
    }

    private fun upperSlots(count: Int, shoulders: Boolean): List<Slot> = buildList {
        add(Slot(setOf(MovementPattern.HORIZONTAL_PUSH), true))
        add(Slot(setOf(MovementPattern.HORIZONTAL_PULL), true))
        if (count >= 3) {
            add(Slot(if (shoulders) setOf(MovementPattern.ISOLATION) else setOf(MovementPattern.VERTICAL_PUSH), false))
        }
        if (count >= 4) add(Slot(setOf(MovementPattern.CORE), false))
        if (count >= 5) {
            add(Slot(if (shoulders) setOf(MovementPattern.HORIZONTAL_PULL) else setOf(MovementPattern.ISOLATION), false))
        }
        if (count >= 6) add(Slot(setOf(MovementPattern.HORIZONTAL_PUSH), false))
        if (count >= 7) {
            add(
                Slot(
                    if (shoulders) setOf(MovementPattern.CORE) else setOf(MovementPattern.HORIZONTAL_PULL),
                    false,
                ),
            )
        }
    }

    private fun lowerSlots(
        count: Int,
        lower: MovementPattern,
        other: MovementPattern,
        knees: Boolean,
        shoulders: Boolean,
    ): List<Slot> = buildList {
        add(Slot(setOf(lower), true))
        add(Slot(setOf(other), false))
        if (count >= 3) add(Slot(if (knees) setOf(MovementPattern.CORE) else setOf(MovementPattern.LUNGE), false))
        if (count >= 4) add(Slot(setOf(MovementPattern.CORE), false))
        if (count >= 5) add(Slot(setOf(MovementPattern.ISOLATION), false))
        if (count >= 6) add(Slot(if (knees) setOf(MovementPattern.ISOLATION) else setOf(MovementPattern.LUNGE), false))
        if (count >= 7) {
            val seventh = when {
                !shoulders -> setOf(MovementPattern.VERTICAL_PUSH)
                knees -> setOf(MovementPattern.HORIZONTAL_PUSH)
                else -> setOf(MovementPattern.LUNGE)
            }
            add(Slot(seventh, false))
        }
    }
}
