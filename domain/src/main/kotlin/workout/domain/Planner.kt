package workout.domain

import java.time.LocalDate

object Planner {
    fun checkPlan(
        plan: SessionPlan,
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
        today: LocalDate,
    ): List<Violation> = Checker.checkPlan(plan, profile, catalog, history, today)

    fun fallbackPlan(
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
        today: LocalDate,
    ): SessionPlan = Fallback.plan(profile, catalog, history, today)

    fun resizeSession(
        plan: SessionPlan,
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
        delta: Int,
    ): SessionPlan = WorkoutSize.resize(plan, profile, catalog, history, delta)

    fun additionChoices(plan: SessionPlan, profile: UserProfile, catalog: Catalog): List<ProgrammedExercise> =
        WorkoutSize.additionChoices(plan, profile, catalog)

    fun addExercise(plan: SessionPlan, profile: UserProfile, catalog: Catalog, exerciseId: String): SessionPlan =
        WorkoutSize.add(plan, profile, catalog, exerciseId)

    fun removeExercise(plan: SessionPlan, exerciseId: String): SessionPlan =
        WorkoutSize.remove(plan, exerciseId)

    fun restoreExercise(plan: SessionPlan, planned: PlannedExercise, index: Int): SessionPlan =
        WorkoutSize.restore(plan, planned, index)

    fun reorderSession(plan: SessionPlan, idsInOrder: List<String>): SessionPlan =
        WorkoutSize.reorder(plan, idsInOrder)

    fun adoptVersion(
        plan: SessionPlan,
        catalog: Catalog,
        profile: UserProfile,
        chosenExerciseId: String,
        replacingExerciseId: String? = null,
    ): AdjustmentResult = Adjustments.adopt(plan, catalog, profile, chosenExerciseId, replacingExerciseId)

    fun applyAdjustment(
        plan: SessionPlan,
        catalog: Catalog,
        profile: UserProfile,
        exerciseId: String,
        direction: AdjustmentDirection,
        startedExerciseIds: Set<String> = emptySet(),
    ): AdjustmentResult = Adjustments.apply(plan, catalog, profile, exerciseId, direction, startedExerciseIds)

    fun prepareCoachRequest(
        systemPrompt: String,
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
        today: LocalDate,
    ): CoachRequest = CoachBrief.prepare(systemPrompt, profile, catalog, history, today)
}
