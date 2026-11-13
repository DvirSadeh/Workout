package workout.domain

object CuratedCatalog {
    val entries: List<CuratedEntry> = listOf(
        entry("Bodyweight_Squat", MovementPattern.SQUAT, LoadType.BODYWEIGHT, "squat", 0),
        entry("Dumbbell_Squat", MovementPattern.SQUAT, LoadType.DUMBBELL, "squat", 1),
        entry("Plie_Dumbbell_Squat", MovementPattern.SQUAT, LoadType.DUMBBELL, "squat", 2),
        entry("Butt_Lift_Bridge", MovementPattern.HINGE, LoadType.BODYWEIGHT, "hinge", 0),
        entry("Single_Leg_Glute_Bridge", MovementPattern.HINGE, LoadType.BODYWEIGHT, "hinge", 1),
        entry("Stiff-Legged_Dumbbell_Deadlift", MovementPattern.HINGE, LoadType.DUMBBELL, "hinge", 2, limits = setOf(LimitTag.LOWER_BACK)),
        entry("Superman", MovementPattern.HINGE, LoadType.BODYWEIGHT, "hinge", 3, limits = setOf(LimitTag.LOWER_BACK)),
        entry("Dumbbell_Rear_Lunge", MovementPattern.LUNGE, LoadType.DUMBBELL, "lunge", 0, limits = setOf(LimitTag.KNEES)),
        entry("Dumbbell_Lunges", MovementPattern.LUNGE, LoadType.DUMBBELL, "lunge", 1, limits = setOf(LimitTag.KNEES)),
        entry("Split_Squat_with_Dumbbells", MovementPattern.LUNGE, LoadType.DUMBBELL, "lunge", 2, limits = setOf(LimitTag.KNEES)),
        entry("Dumbbell_Step_Ups", MovementPattern.LUNGE, LoadType.DUMBBELL, "lunge", 3, bench = true, limits = setOf(LimitTag.KNEES)),
        entry("Incline_Push-Up", MovementPattern.HORIZONTAL_PUSH, LoadType.BODYWEIGHT, "horizontal_push", 0, limits = setOf(LimitTag.WRISTS)),
        entry("Pushups", MovementPattern.HORIZONTAL_PUSH, LoadType.BODYWEIGHT, "horizontal_push", 1, limits = setOf(LimitTag.WRISTS)),
        entry("Push-Ups_With_Feet_Elevated", MovementPattern.HORIZONTAL_PUSH, LoadType.BODYWEIGHT, "horizontal_push", 2, limits = setOf(LimitTag.WRISTS)),
        entry("Dumbbell_Floor_Press", MovementPattern.HORIZONTAL_PUSH, LoadType.DUMBBELL, "horizontal_push", 3),
        entry("Dumbbell_Bench_Press", MovementPattern.HORIZONTAL_PUSH, LoadType.DUMBBELL, "horizontal_push", 4, bench = true),
        entry("One-Arm_Dumbbell_Row", MovementPattern.HORIZONTAL_PULL, LoadType.DUMBBELL, "row", 0),
        entry("Bent_Over_Two-Dumbbell_Row", MovementPattern.HORIZONTAL_PULL, LoadType.DUMBBELL, "row", 1),
        entry("Bent_Over_Two-Dumbbell_Row_With_Palms_In", MovementPattern.HORIZONTAL_PULL, LoadType.DUMBBELL, "row", 2),
        entry("Reverse_Flyes", MovementPattern.HORIZONTAL_PULL, LoadType.DUMBBELL, "rear_delt", 0, limits = setOf(LimitTag.SHOULDERS)),
        entry("Standing_Dumbbell_Press", MovementPattern.VERTICAL_PUSH, LoadType.DUMBBELL, "vertical_push", 0, limits = setOf(LimitTag.SHOULDERS)),
        entry("Dumbbell_Shoulder_Press", MovementPattern.VERTICAL_PUSH, LoadType.DUMBBELL, "vertical_push", 1, limits = setOf(LimitTag.SHOULDERS)),
        entry("Standing_Palms-In_Dumbbell_Press", MovementPattern.VERTICAL_PUSH, LoadType.DUMBBELL, "vertical_push", 2, limits = setOf(LimitTag.SHOULDERS)),
        entry("See-Saw_Press_Alternating_Side_Press", MovementPattern.VERTICAL_PUSH, LoadType.DUMBBELL, "vertical_push", 3, limits = setOf(LimitTag.SHOULDERS)),
        entry("Dead_Bug", MovementPattern.CORE, LoadType.BODYWEIGHT, "core", 0),
        entry("Crunches", MovementPattern.CORE, LoadType.BODYWEIGHT, "core", 1),
        entry("Plank", MovementPattern.CORE, LoadType.BODYWEIGHT, "core", 2),
        entry("Side_Bridge", MovementPattern.CORE, LoadType.BODYWEIGHT, "core", 3),
        entry("Reverse_Crunch", MovementPattern.CORE, LoadType.BODYWEIGHT, "core", 4),
        entry("Dumbbell_Bicep_Curl", MovementPattern.ISOLATION, LoadType.DUMBBELL, "curl", 0),
        entry("Hammer_Curls", MovementPattern.ISOLATION, LoadType.DUMBBELL, "hammer_curl", 0),
        entry("Seated_Dumbbell_Curl", MovementPattern.ISOLATION, LoadType.DUMBBELL, "seated_curl", 0),
        entry("Concentration_Curls", MovementPattern.ISOLATION, LoadType.DUMBBELL, "concentration_curl", 0),
        entry("Standing_Dumbbell_Calf_Raise", MovementPattern.ISOLATION, LoadType.DUMBBELL, "calf_raise", 0),
        entry("Side_Lateral_Raise", MovementPattern.ISOLATION, LoadType.DUMBBELL, "lateral_raise", 0, limits = setOf(LimitTag.SHOULDERS)),
    )

    fun catalog(texts: Map<String, ExerciseText> = emptyMap()): Catalog {
        val exercises = entries.map { entry ->
            val text = texts[entry.id]
            ProgrammedExercise(
                id = entry.id,
                name = text?.name ?: entry.id.replace('_', ' '),
                instructions = text?.instructions ?: emptyList(),
                primaryMuscles = text?.primaryMuscles ?: emptyList(),
                imageFiles = text?.imageFiles ?: emptyList(),
                pattern = entry.pattern,
                loadType = entry.loadType,
                needsBench = entry.needsBench,
                familyId = entry.familyId,
                tier = entry.tier,
                excludedBy = entry.excludedBy,
            )
        }
        return Catalog(exercises)
    }

    private fun entry(
        id: String,
        pattern: MovementPattern,
        loadType: LoadType,
        family: String,
        tier: Int,
        bench: Boolean = false,
        limits: Set<LimitTag> = emptySet(),
    ) = CuratedEntry(id, pattern, loadType, bench, family, tier, limits)
}
