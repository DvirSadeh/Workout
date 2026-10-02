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

    fun removeExercise(plan: SessionPlan, exerciseId: String): SessionPlan =
        WorkoutSize.remove(plan, exerciseId)

    fun reorderSession(plan: SessionPlan, idsInOrder: List<String>): SessionPlan =
        WorkoutSize.reorder(plan, idsInOrder)

    fun adoptVersion(
        plan: SessionPlan,
        catalog: Catalog,
        profile: UserProfile,
        chosenExerciseId: String,
    ): AdjustmentResult = Adjustments.adopt(plan, catalog, profile, chosenExerciseId)

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
