package workout.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannerTest {
    private val catalog = CuratedCatalog.catalog()
    private val start: LocalDate = LocalDate.of(2026, 1, 5)

    @Test
    fun sameInputsBuildTheSameSession() {
        val profile = profile()
        val first = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val second = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        assertEquals(first, second)
    }

    @Test
    fun newUserFullBodyPassesTheChecker() {
        val profile = profile()
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        assertEquals(DayFocus.FULL_BODY, plan.focus)
        assertEquals(6, plan.exercises.size)
        assertTrue(plan.note.contains("kg"))
        assertClean(plan, profile, emptyList(), start)
    }

    @Test
    fun noBenchSkipsBenchPress() {
        val profile = profile(bench = false)
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        assertTrue(plan.exercises.none { it.exerciseId == "Dumbbell_Bench_Press" })
        assertTrue(plan.exercises.none { it.exerciseId == "Dumbbell_Step_Ups" })
        assertClean(plan, profile, emptyList(), start)
    }

    @Test
    fun shoulderLimitSkipsOverheadPress() {
        val profile = profile(limits = setOf(LimitTag.SHOULDERS))
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val overhead = setOf(
            "Standing_Dumbbell_Press",
            "Dumbbell_Shoulder_Press",
            "Standing_Palms-In_Dumbbell_Press",
            "See-Saw_Press_Alternating_Side_Press",
            "Side_Lateral_Raise",
        )
        assertTrue(plan.exercises.none { it.exerciseId in overhead })
        assertClean(plan, profile, emptyList(), start)
    }

    @Test
    fun kneeLimitSkipsLunges() {
        val profile = profile(limits = setOf(LimitTag.KNEES))
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        assertTrue(plan.exercises.none { catalog.require(it.exerciseId).pattern == MovementPattern.LUNGE })
        assertClean(plan, profile, emptyList(), start)
    }

    @Test
    fun sessionLengthChangesTheDose() {
        val short = Planner.fallbackPlan(profile(minutes = 20), catalog, emptyList(), start)
        val long = Planner.fallbackPlan(profile(minutes = 60), catalog, emptyList(), start)
        assertEquals(4, short.exercises.size)
        assertEquals(2, short.exercises.first().sets)
        assertEquals(7, long.exercises.size)
        assertEquals(4, long.exercises.first().sets)
        assertClean(short, profile(minutes = 20), emptyList(), start)
        assertClean(long, profile(minutes = 60), emptyList(), start)
    }

    @Test
    fun goalAndAgeChangeRest() {
        val strength = Planner.fallbackPlan(profile(goal = Goal.STRENGTH), catalog, emptyList(), start)
        val fatLoss = Planner.fallbackPlan(profile(goal = Goal.FAT_LOSS), catalog, emptyList(), start)
        val older = Planner.fallbackPlan(profile(birthYear = 1968), catalog, emptyList(), start)
        assertEquals(90, strength.exercises.first().restSeconds)
        assertEquals(45, fatLoss.exercises.first().restSeconds)
        assertEquals(75, older.exercises.first().restSeconds)
    }

    @Test
    fun fourDaysAlternateUpperAndLower() {
        val profile = profile(days = 4)
        val first = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        assertEquals(DayFocus.UPPER, first.focus)
        val history = listOf(record(first, SessionRating.JUST_RIGHT))
        val second = Planner.fallbackPlan(profile, catalog, history, start.plusDays(2))
        assertEquals(DayFocus.LOWER, second.focus)
        assertClean(first, profile, emptyList(), start)
        assertClean(second, profile, history, start.plusDays(2))
    }

    @Test
    fun extrasAreNotRepeatedAcrossTheLastTwoSessions() {
        val profile = profile()
        var history = emptyList<SessionRecord>()
        var day = start
        repeat(4) {
            val plan = Planner.fallbackPlan(profile, catalog, history, day)
            assertClean(plan, profile, history, day)
            val recentExtras = history.takeLast(2).flatMap { session ->
                session.exercises.filter { !it.anchor }.map { it.exerciseId }
            }.toSet()
            plan.exercises.filter { !it.anchor }.forEach { exercise ->
                assertTrue(exercise.exerciseId !in recentExtras, exercise.exerciseId)
            }
            history = history + record(plan, SessionRating.JUST_RIGHT)
            day = day.plusDays(2)
        }
    }

    @Test
    fun hittingTheTopOfTheRangeRaisesTheDumbbellOneStep() {
        val profile = profile(dumbbells = listOf(4.0, 8.0, 12.0))
        val first = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val loaded = first.exercises.first { it.loadKg != null && it.anchor }
        val history = listOf(record(first, SessionRating.JUST_RIGHT, reps = loaded.repsHigh))
        val second = Planner.fallbackPlan(profile, catalog, history, start.plusDays(2))
        val next = second.exercises.first { it.exerciseId == loaded.exerciseId }
        assertEquals(heavier(profile.dumbbellKg, loaded.loadKg!!), next.loadKg)
        assertClean(second, profile, history, start.plusDays(2))
    }

    @Test
    fun prescribedLoadsAreOwnedDumbbells() {
        val profile = profile(dumbbells = listOf(4.0, 7.5, 10.0))
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        plan.exercises.forEach { exercise ->
            val load = exercise.loadKg ?: return@forEach
            assertTrue(profile.dumbbellKg.any { kotlin.math.abs(it - load) < 0.05 })
        }
    }

    @Test
    fun missedRepsDropTheLoad() {
        val profile = profile(dumbbells = listOf(4.0, 8.0, 12.0, 16.0))
        val seeded = sessionWithLoad("Dumbbell_Squat", 12.0, reps = 4, repsLow = 8, repsHigh = 15, anchor = true)
        val today = start.plusDays(2)
        val plan = Planner.fallbackPlan(profile, catalog, listOf(seeded), today)
        val squat = plan.exercises.first { it.exerciseId == "Dumbbell_Squat" }
        assertEquals(8.0, squat.loadKg)
        assertClean(plan, profile, listOf(seeded), today)
    }

    @Test
    fun harderOnABodyweightLiftMovesUpATier() {
        val profile = profile()
        val first = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val push = first.exercises.first { catalog.require(it.exerciseId).pattern == MovementPattern.HORIZONTAL_PUSH }
        val adjusted = record(first, SessionRating.JUST_RIGHT).let { session ->
            session.copy(
                adjustments = listOf(
                    AdjustmentRecord(push.exerciseId, "horizontal_push", AdjustmentDirection.HARDER),
                ),
            )
        }
        val today = start.plusDays(2)
        val second = Planner.fallbackPlan(profile, catalog, listOf(adjusted), today)
        val next = second.exercises.first { it.anchor && catalog.require(it.exerciseId).pattern == MovementPattern.HORIZONTAL_PUSH }
        assertTrue(catalog.require(next.exerciseId).tier > catalog.require(push.exerciseId).tier)
        assertClean(second, profile, listOf(adjusted), today)
    }

    @Test
    fun anUnfinishedSessionDoesNotRaiseTheWeight() {
        val profile = profile(dumbbells = listOf(4.0, 8.0, 12.0))
        val first = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val loaded = first.exercises.first { it.loadKg != null && it.anchor }
        val history = listOf(record(first, rating = null, reps = loaded.repsHigh))
        val second = Planner.fallbackPlan(profile, catalog, history, start.plusDays(2))
        val next = second.exercises.first { it.exerciseId == loaded.exerciseId }
        assertEquals(loaded.loadKg, next.loadKg)
        assertClean(second, profile, history, start.plusDays(2))
    }

    @Test
    fun tooHardHoldsTheWeight() {
        val profile = profile(dumbbells = listOf(4.0, 8.0, 12.0))
        val first = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val history = listOf(record(first, SessionRating.TOO_HARD, reps = first.exercises.first().repsHigh))
        val second = Planner.fallbackPlan(profile, catalog, history, start.plusDays(2))
        first.exercises.filter { it.anchor && it.loadKg != null }.forEach { exercise ->
            val next = second.exercises.first { it.exerciseId == exercise.exerciseId }
            assertTrue((next.loadKg ?: 0.0) <= (exercise.loadKg ?: 0.0) + 0.05)
        }
        assertClean(second, profile, history, start.plusDays(2))
    }

    @Test
    fun tooEasyProgressesAMainLift() {
        val profile = profile(dumbbells = listOf(4.0, 8.0, 12.0, 16.0))
        val first = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val history = listOf(record(first, SessionRating.TOO_EASY, reps = 8))
        val today = start.plusDays(2)
        val second = Planner.fallbackPlan(profile, catalog, history, today)
        val progressed = second.exercises.filter { it.anchor }.any { next ->
            val previous = first.exercises.firstOrNull { it.exerciseId == next.exerciseId }
            previous != null && next.loadKg != null && previous.loadKg != null && next.loadKg > previous.loadKg
        }
        assertTrue(progressed)
        assertClean(second, profile, history, today)
    }

    @Test
    fun easierTapDoesNotMakeThatFamilyHarder() {
        val profile = profile(dumbbells = listOf(4.0, 8.0, 12.0, 16.0))
        val seeded = sessionWithLoad("Dumbbell_Floor_Press", 12.0, reps = 10, repsLow = 8, repsHigh = 15, anchor = true)
            .copy(adjustments = listOf(AdjustmentRecord("Dumbbell_Floor_Press", "horizontal_push", AdjustmentDirection.EASIER)))
        val today = start.plusDays(2)
        val plan = Planner.fallbackPlan(profile, catalog, listOf(seeded), today)
        val press = plan.exercises.first { catalog.require(it.exerciseId).familyId == "horizontal_push" && it.anchor }
        assertTrue((press.loadKg ?: 0.0) <= 12.0)
        assertTrue(catalog.require(press.exerciseId).tier <= catalog.require("Dumbbell_Floor_Press").tier)
        assertClean(plan, profile, listOf(seeded), today)
    }

    @Test
    fun deloadWeekIsLighter() {
        val profile = profile(dumbbells = listOf(4.0, 8.0, 12.0))
        val earlier = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val history = listOf(record(earlier, SessionRating.JUST_RIGHT, reps = 10))
        val deloadDay = start.plusDays(21)
        val plan = Planner.fallbackPlan(profile, catalog, history, deloadDay)
        assertTrue(plan.deload)
        assertEquals(2, plan.exercises.first().sets)
        assertEquals(plan.exercises.first().repsLow, plan.exercises.first().repsHigh)
        assertClean(plan, profile, history, deloadDay)
    }

    @Test
    fun aNewTraineeOverSixtyStartsEasier() {
        val older = Planner.fallbackPlan(
            profile(birthYear = 1960, experience = Experience.NEW),
            catalog,
            emptyList(),
            start,
        )
        assertEquals(2, older.exercises.first().sets)
        assertClean(older, profile(birthYear = 1960, experience = Experience.NEW), emptyList(), start)
    }

    @Test
    fun easierAlsoSoftensLaterWorkInTheSamePattern() {
        val profile = profile(days = 4, minutes = 60)
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val firstPush = plan.exercises.indexOfFirst { catalog.require(it.exerciseId).pattern == MovementPattern.HORIZONTAL_PUSH }
        val laterPush = plan.exercises.indexOfLast { catalog.require(it.exerciseId).pattern == MovementPattern.HORIZONTAL_PUSH }
        assertNotEquals(firstPush, laterPush)
        val result = Planner.applyAdjustment(
            plan,
            catalog,
            profile,
            plan.exercises[firstPush].exerciseId,
            AdjustmentDirection.EASIER,
        )
        assertTrue(result.adjustments.size >= 2)
        assertTrue(result.message.contains("easier"))
    }

    @Test
    fun choosingAVersionReplacesOnlyThatExercise() {
        val profile = profile()
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        assertEquals("Bodyweight_Squat", plan.exercises.first { it.anchor && catalog.require(it.exerciseId).pattern == MovementPattern.SQUAT }.exerciseId)
        val result = Planner.adoptVersion(plan, catalog, profile, "Dumbbell_Squat")
        val squat = result.plan.exercises.first { it.exerciseId == "Dumbbell_Squat" }
        assertEquals(1, result.adjustments.size)
        assertEquals(AdjustmentDirection.CHOSEN, result.adjustments.single().direction)
        assertEquals("squat", result.adjustments.single().familyId)
        assertTrue(squat.loadKg != null && squat.loadKg in profile.dumbbellKg)
        assertEquals(1, result.plan.exercises.countIndexed { index, exercise -> exercise != plan.exercises[index] })

        val heavier = Planner.adoptVersion(result.plan, catalog, profile, "Plie_Dumbbell_Squat")
        assertEquals(squat.loadKg, heavier.plan.exercises.first { it.exerciseId == "Plie_Dumbbell_Squat" }.loadKg)

        val back = Planner.adoptVersion(heavier.plan, catalog, profile, "Bodyweight_Squat")
        assertNull(back.plan.exercises.first { it.exerciseId == "Bodyweight_Squat" }.loadKg)
    }

    @Test
    fun choosingAVersionThePlanCannotDoIsRefused() {
        val profile = profile(bench = false)
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val bench = Planner.adoptVersion(plan, catalog, profile, "Dumbbell_Bench_Press")
        assertTrue(bench.adjustments.isEmpty())
        assertEquals(plan, bench.plan)

        val limited = Planner.adoptVersion(plan, catalog, profile.copy(limits = setOf(LimitTag.WRISTS)), "Pushups")
        assertTrue(limited.adjustments.isEmpty())
        assertEquals(plan, limited.plan)

        val other = Planner.adoptVersion(plan, catalog, profile, "Hammer_Curls")
        assertTrue(other.adjustments.isEmpty())
        assertEquals(plan, other.plan)
    }

    @Test
    fun aChosenVersionStaysTheMainLiftForTheBlock() {
        val profile = profile()
        val first = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val firstDone = record(first, SessionRating.JUST_RIGHT)
        val secondDay = start.plusDays(2)
        val second = Planner.fallbackPlan(profile, catalog, listOf(firstDone), secondDay)
        assertEquals("Bodyweight_Squat", second.exercises.first { it.anchor && catalog.require(it.exerciseId).pattern == MovementPattern.SQUAT }.exerciseId)
        val chosen = Planner.adoptVersion(second, catalog, profile, "Dumbbell_Squat")
        val secondDone = record(chosen.plan, SessionRating.JUST_RIGHT).copy(adjustments = chosen.adjustments)
        val history = listOf(firstDone, secondDone)
        val thirdDay = start.plusDays(4)
        val third = Planner.fallbackPlan(profile, catalog, history, thirdDay)
        assertEquals("Dumbbell_Squat", third.exercises.first { it.anchor && catalog.require(it.exerciseId).pattern == MovementPattern.SQUAT }.exerciseId)
        assertClean(third, profile, history, thirdDay)
        val fourthDay = start.plusDays(6)
        val fourth = Planner.fallbackPlan(profile, catalog, history + record(third, SessionRating.JUST_RIGHT), fourthDay)
        assertEquals("Dumbbell_Squat", fourth.exercises.first { it.anchor && catalog.require(it.exerciseId).pattern == MovementPattern.SQUAT }.exerciseId)
        assertClean(fourth, profile, history + record(third, SessionRating.JUST_RIGHT), fourthDay)
    }

    @Test
    fun shorterDropsTheLastExerciseAndLongerAddsOne() {
        val profile = profile(minutes = 20)
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        assertEquals(4, plan.exercises.size)
        val short = Planner.resizeSession(plan, profile, catalog, emptyList(), -1)
        assertEquals(plan.exercises.take(3), short.exercises)
        assertEquals(short, Planner.resizeSession(short, profile, catalog, emptyList(), -1))
        val long = Planner.resizeSession(plan, profile, catalog, emptyList(), 1)
        assertEquals(plan.exercises, long.exercises.take(4))
        assertEquals(MovementPattern.CORE, catalog.require(long.exercises.last().exerciseId).pattern)
        assertEquals(plan.exercises.first().sets, long.exercises.last().sets)
        var grown = plan
        repeat(6) { grown = Planner.resizeSession(grown, profile, catalog, emptyList(), 1) }
        assertEquals(7, grown.exercises.size)
        assertEquals(grown, Planner.resizeSession(grown, profile, catalog, emptyList(), 1))
    }

    @Test
    fun additionChoicesSkipExercisesAlreadyPlannedAndEquipmentLimits() {
        val profile = profile(minutes = 20, limits = setOf(LimitTag.SHOULDERS))
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val choices = Planner.additionChoices(plan, profile, catalog)
        assertTrue(choices.isNotEmpty())
        assertTrue(choices.none { choice -> plan.exercises.any { it.exerciseId == choice.id } })
        assertTrue(choices.none { it.id == "Standing_Dumbbell_Press" })
        val picked = choices.first { it.pattern == MovementPattern.CORE }
        val added = Planner.addExercise(plan, profile, catalog, picked.id)
        assertEquals(plan.exercises, added.exercises.dropLast(1))
        assertEquals(picked.id, added.exercises.last().exerciseId)
        assertEquals(plan.exercises.maxOf { it.sets }, added.exercises.last().sets)
        assertEquals(added, Planner.addExercise(added, profile, catalog, picked.id))
        assertEquals(plan, Planner.addExercise(plan, profile, catalog, "Made_Up_Lift"))
        var full = plan
        repeat(6) { full = Planner.resizeSession(full, profile, catalog, emptyList(), 1) }
        assertEquals(7, full.exercises.size)
        assertTrue(Planner.additionChoices(full, profile, catalog).isEmpty())
        assertEquals(full, Planner.addExercise(full, profile, catalog, picked.id))
    }

    @Test
    fun removeDropsThatExerciseAndStopsAtTheMinimum() {
        val profile = profile(minutes = 20)
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val removedId = plan.exercises[1].exerciseId
        val removed = Planner.removeExercise(plan, removedId)
        assertEquals(listOf(plan.exercises[0], plan.exercises[2], plan.exercises[3]), removed.exercises)
        assertEquals(removed, Planner.removeExercise(removed, removed.exercises.first().exerciseId))
        assertEquals(plan, Planner.removeExercise(plan, "missing"))
        val restored = Planner.restoreExercise(removed, plan.exercises[1], 1)
        assertEquals(plan.exercises, restored.exercises)
        assertEquals(plan, Planner.restoreExercise(plan, plan.exercises[1], 0))
    }

    @Test
    fun reorderKeepsTheSameExercisesInTheRequestedOrder() {
        val profile = profile(minutes = 20)
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val ids = plan.exercises.map { it.exerciseId }
        val moved = Planner.reorderSession(plan, listOf(ids[2], ids[0], ids[1], ids[3]))
        assertEquals(listOf(plan.exercises[2], plan.exercises[0], plan.exercises[1], plan.exercises[3]), moved.exercises)
        assertEquals(plan, Planner.reorderSession(plan, ids))
        assertEquals(plan, Planner.reorderSession(plan, listOf(ids[0], ids[0], ids[1], ids[2])))
    }

    @Test
    fun harderLeavesTheRestOfTheSessionAlone() {
        val profile = profile(minutes = 60)
        val plan = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val first = plan.exercises.first { it.loadKg != null }
        val result = Planner.applyAdjustment(plan, catalog, profile, first.exerciseId, AdjustmentDirection.HARDER)
        assertEquals(1, result.adjustments.size)
        val changed = result.plan.exercises.countIndexed { index, exercise ->
            exercise != plan.exercises[index]
        }
        assertEquals(1, changed)
    }

    @Test
    fun checkerRejectsAnInventedExerciseABadLoadAndADuplicate() {
        val profile = profile()
        val legal = Planner.fallbackPlan(profile, catalog, emptyList(), start)
        val invented = legal.copy(
            exercises = legal.exercises.mapIndexed { index, exercise ->
                if (index == 0) exercise.copy(exerciseId = "Made_Up_Lift") else exercise
            },
        )
        assertTrue(Planner.checkPlan(invented, profile, catalog, emptyList(), start).any { it.code == "unknown" })

        val heavy = legal.copy(
            exercises = legal.exercises.map { exercise ->
                if (exercise.loadKg != null) exercise.copy(loadKg = 100.0) else exercise
            },
        )
        assertTrue(Planner.checkPlan(heavy, profile, catalog, emptyList(), start).any { it.code == "load" || it.code == "start" || it.code == "step" })

        val duplicate = legal.copy(exercises = legal.exercises.map { it.copy(exerciseId = legal.exercises.first().exerciseId) })
        assertTrue(Planner.checkPlan(duplicate, profile, catalog, emptyList(), start).any { it.code == "duplicate" })
    }

    @Test
    fun coachPayloadMentionsTheLogAndTheMenu() {
        val profile = profile()
        val request = Planner.prepareCoachRequest("Be a coach.", profile, catalog, emptyList(), start)
        assertTrue(request.payloadJson.contains("Bodyweight_Squat"))
        assertTrue(request.payloadJson.contains("\"deload\":false"))
        assertEquals("Be a coach.", request.systemPrompt)
    }

    @Test
    fun fixturesThePlanCallsForAllPass() {
        val fresh = profile()
        assertClean(Planner.fallbackPlan(fresh, catalog, emptyList(), start), fresh, emptyList(), start)

        val hardProfile = profile()
        val hardPlan = Planner.fallbackPlan(hardProfile, catalog, emptyList(), start)
        val hardHistory = listOf(record(hardPlan, SessionRating.TOO_HARD))
        val afterHard = Planner.fallbackPlan(hardProfile, catalog, hardHistory, start.plusDays(2))
        assertClean(afterHard, hardProfile, hardHistory, start.plusDays(2))

        val rowProfile = profile()
        val rowPlan = Planner.fallbackPlan(rowProfile, catalog, emptyList(), start)
        val row = rowPlan.exercises.first { it.exerciseId == "One-Arm_Dumbbell_Row" }
        val rowHistory = listOf(
            record(rowPlan, SessionRating.JUST_RIGHT).copy(
                adjustments = listOf(AdjustmentRecord(row.exerciseId, "row", AdjustmentDirection.HARDER)),
            ),
        )
        val afterRow = Planner.fallbackPlan(rowProfile, catalog, rowHistory, start.plusDays(2))
        assertClean(afterRow, rowProfile, rowHistory, start.plusDays(2))

        val deloadProfile = profile()
        val before = listOf(record(Planner.fallbackPlan(deloadProfile, catalog, emptyList(), start), SessionRating.JUST_RIGHT))
        val deloadDay = start.plusDays(21)
        assertClean(Planner.fallbackPlan(deloadProfile, catalog, before, deloadDay), deloadProfile, before, deloadDay)

        val noBench = profile(bench = false)
        assertClean(Planner.fallbackPlan(noBench, catalog, emptyList(), start), noBench, emptyList(), start)
        assertNull(Planner.fallbackPlan(noBench, catalog, emptyList(), start).exercises.find { it.exerciseId == "Dumbbell_Bench_Press" })
    }

    private fun assertClean(plan: SessionPlan, profile: UserProfile, history: List<SessionRecord>, today: LocalDate) {
        val violations = Planner.checkPlan(plan, profile, catalog, history, today)
        assertTrue(violations.isEmpty(), violations.joinToString("\n") { "${it.code}: ${it.message}" })
    }

    private fun profile(
        goal: Goal = Goal.FIT,
        days: Int = 3,
        minutes: Int = 45,
        experience: Experience = Experience.NEW,
        birthYear: Int = 1990,
        bench: Boolean = true,
        limits: Set<LimitTag> = emptySet(),
        dumbbells: List<Double> = listOf(4.0, 6.0, 8.0, 10.0, 12.0, 14.0),
    ) = UserProfile(
        birthYear = birthYear,
        sex = Sex.MALE,
        heightCm = 178,
        weightKg = 80.0,
        experience = experience,
        goal = goal,
        daysPerWeek = days,
        minutesPerSession = minutes,
        dumbbellKg = dumbbells,
        hasBench = bench,
        limits = limits,
        trainingStart = start,
    )

    private fun record(plan: SessionPlan, rating: SessionRating?, reps: Int? = null): SessionRecord {
        val exercises = plan.exercises.map { planned ->
            val doneReps = reps ?: planned.repsLow
            LoggedExercise(
                exerciseId = planned.exerciseId,
                prescribedSets = planned.sets,
                repsLow = planned.repsLow,
                repsHigh = planned.repsHigh,
                prescribedLoadKg = planned.loadKg,
                sets = List(planned.sets) { LoggedSet(doneReps, planned.loadKg, completed = true) },
                anchor = planned.anchor,
            )
        }
        return SessionRecord(
            date = plan.date,
            rating = rating,
            source = plan.source,
            note = plan.note,
            focus = plan.focus,
            exercises = exercises,
            adjustments = emptyList(),
        )
    }

    private fun sessionWithLoad(
        id: String,
        load: Double,
        reps: Int,
        repsLow: Int,
        repsHigh: Int,
        anchor: Boolean,
    ): SessionRecord {
        val focus = DayFocus.FULL_BODY
        return SessionRecord(
            date = start,
            rating = SessionRating.JUST_RIGHT,
            source = PlanSource.FALLBACK,
            note = "Seed session for a unit test of the next plan.",
            focus = focus,
            exercises = listOf(
                LoggedExercise(
                    exerciseId = id,
                    prescribedSets = 3,
                    repsLow = repsLow,
                    repsHigh = repsHigh,
                    prescribedLoadKg = load,
                    sets = List(3) { LoggedSet(reps, load, true) },
                    anchor = anchor,
                ),
            ),
            adjustments = emptyList(),
        )
    }

    private fun heavier(owned: List<Double>, load: Double): Double? = owned.filter { it > load + 0.05 }.minOrNull()

    private inline fun <T> List<T>.countIndexed(predicate: (Int, T) -> Boolean): Int =
        withIndex().count { predicate(it.index, it.value) }
}
