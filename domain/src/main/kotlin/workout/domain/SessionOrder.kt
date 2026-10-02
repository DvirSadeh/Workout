package workout.domain

enum class PlayMode { STRAIGHT, CIRCUIT }

sealed interface PlayStep {
    data class Work(val exerciseIndex: Int, val setIndex: Int) : PlayStep
    data class Rest(val seconds: Int) : PlayStep
}

object SessionOrder {
    fun sequence(setCounts: List<Int>, restSeconds: List<Int>, mode: PlayMode): List<PlayStep> {
        if (setCounts.isEmpty()) return emptyList()
        return when (mode) {
            PlayMode.STRAIGHT -> straight(setCounts, restSeconds)
            PlayMode.CIRCUIT -> circuit(setCounts, restSeconds)
        }
    }

    private fun straight(setCounts: List<Int>, restSeconds: List<Int>): List<PlayStep> {
        val works = mutableListOf<PlayStep.Work>()
        setCounts.forEachIndexed { exercise, count ->
            repeat(count.coerceAtLeast(0)) { set ->
                works += PlayStep.Work(exercise, set)
            }
        }
        val steps = mutableListOf<PlayStep>()
        works.forEachIndexed { index, work ->
            steps += work
            val rest = restSeconds.getOrElse(work.exerciseIndex) { 0 }
            if (index < works.lastIndex && rest > 0) steps += PlayStep.Rest(rest)
        }
        return steps
    }

    private fun circuit(setCounts: List<Int>, restSeconds: List<Int>): List<PlayStep> {
        val rounds = setCounts.maxOrNull()?.coerceAtLeast(0) ?: return emptyList()
        val steps = mutableListOf<PlayStep>()
        for (round in 0 until rounds) {
            val inRound = mutableListOf<Int>()
            setCounts.forEachIndexed { exercise, count ->
                if (round < count) {
                    steps += PlayStep.Work(exercise, round)
                    inRound += exercise
                }
            }
            val anotherRound = setCounts.any { it > round + 1 }
            if (anotherRound && inRound.isNotEmpty()) {
                val rest = inRound.maxOf { restSeconds.getOrElse(it) { 0 } }
                if (rest > 0) steps += PlayStep.Rest(rest)
            }
        }
        return steps
    }
}
