package workout.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TrainingProgressTest {
    private val first = LocalDate.of(2026, 10, 2)
    private val later = first.plusDays(3)

    @Test
    fun heavierLaterWorkoutSitsHigherThanTheFirst() {
        val lines = TrainingProgress.lines(
            listOf(
                session(first, "Row", LoggedSet(8, 10.0, true), LoggedSet(8, 10.0, true)),
                session(later, "Row", LoggedSet(8, 20.0, true), LoggedSet(8, 20.0, true)),
            ),
        ) { listOf("middle back") }
        val line = lines.single()
        assertEquals("middle back", line.muscle)
        assertEquals(listOf(1.0, 2.0), line.points.map { it.level })
        assertEquals(listOf(first, later), line.points.map { it.date })
    }

    @Test
    fun bodyweightWorkIsReps() {
        val lines = TrainingProgress.lines(
            listOf(
                session(first, "Push-Up", LoggedSet(10, null, true)),
                session(later, "Push-Up", LoggedSet(15, null, true)),
            ),
        ) { listOf("chest") }
        assertEquals(listOf(1.0, 1.5), lines.single().points.map { it.level })
    }

    @Test
    fun anUnfinishedSetDoesNotCount() {
        val lines = TrainingProgress.lines(
            listOf(session(first, "Row", LoggedSet(8, 10.0, false))),
        ) { listOf("middle back") }
        assertEquals(emptyList(), lines)
    }

    @Test
    fun eachPrimaryMuscleGetsTheSameWork() {
        val lines = TrainingProgress.lines(
            listOf(session(first, "Row", LoggedSet(8, 10.0, true))),
        ) { listOf("middle back", "biceps") }
        assertEquals(listOf("middle back", "biceps"), lines.map { it.muscle })
        assertEquals(listOf(1.0, 1.0), lines.map { it.points.single().level })
    }

    private fun session(date: LocalDate, id: String, vararg sets: LoggedSet) = SessionRecord(
        date = date,
        rating = SessionRating.JUST_RIGHT,
        source = PlanSource.FALLBACK,
        note = "",
        focus = DayFocus.FULL_BODY,
        exercises = listOf(
            LoggedExercise(
                exerciseId = id,
                prescribedSets = sets.size,
                repsLow = 8,
                repsHigh = 12,
                prescribedLoadKg = sets.first().loadKg,
                sets = sets.toList(),
                anchor = true,
            ),
        ),
        adjustments = emptyList(),
    )
}
