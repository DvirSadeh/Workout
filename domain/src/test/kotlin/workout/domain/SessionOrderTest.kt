package workout.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionOrderTest {
    @Test
    fun straightSetsRestAfterEverySetExceptTheLast() {
        val steps = SessionOrder.sequence(listOf(2, 2), listOf(75, 45), PlayMode.STRAIGHT)
        assertEquals(
            listOf(
                PlayStep.Work(0, 0),
                PlayStep.Rest(75),
                PlayStep.Work(0, 1),
                PlayStep.Rest(75),
                PlayStep.Work(1, 0),
                PlayStep.Rest(45),
                PlayStep.Work(1, 1),
            ),
            steps,
        )
    }

    @Test
    fun circuitDoesOneSetOfEachExerciseThenRestsThenGoesAgain() {
        val steps = SessionOrder.sequence(listOf(2, 2, 2, 2), listOf(75, 75, 75, 75), PlayMode.CIRCUIT)
        assertEquals(
            listOf(
                PlayStep.Work(0, 0),
                PlayStep.Work(1, 0),
                PlayStep.Work(2, 0),
                PlayStep.Work(3, 0),
                PlayStep.Rest(75),
                PlayStep.Work(0, 1),
                PlayStep.Work(1, 1),
                PlayStep.Work(2, 1),
                PlayStep.Work(3, 1),
            ),
            steps,
        )
    }

    @Test
    fun circuitRestUsesTheLongestRestInTheRoundAndSkipsAFinishedExercise() {
        val steps = SessionOrder.sequence(listOf(1, 2), listOf(60, 90), PlayMode.CIRCUIT)
        assertEquals(
            listOf(
                PlayStep.Work(0, 0),
                PlayStep.Work(1, 0),
                PlayStep.Rest(90),
                PlayStep.Work(1, 1),
            ),
            steps,
        )
    }

    @Test
    fun noRestWhenThePlanAsksForNoneOrTheWorkoutIsASingleSet() {
        assertEquals(
            listOf(PlayStep.Work(0, 0), PlayStep.Work(0, 1)),
            SessionOrder.sequence(listOf(2), listOf(0), PlayMode.STRAIGHT),
        )
        assertEquals(
            listOf(PlayStep.Work(0, 0)),
            SessionOrder.sequence(listOf(1), listOf(90), PlayMode.CIRCUIT),
        )
        assertEquals(emptyList(), SessionOrder.sequence(emptyList(), emptyList(), PlayMode.STRAIGHT))
    }
}
