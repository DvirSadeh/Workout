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
