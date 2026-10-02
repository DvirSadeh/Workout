package workout.domain

import java.time.LocalDate

object CoachBrief {
    fun prepare(
        systemPrompt: String,
        profile: UserProfile,
        catalog: Catalog,
        history: List<SessionRecord>,
        today: LocalDate,
    ): CoachRequest {
        val dose = Training.dose(profile, today)
        val focus = Training.focus(profile, history, today)
        val block = Training.blockIndex(profile, today)
        val slots = Training.slots(focus, dose.movementCount, block, profile.limits)
        val locks = Signals.locks(profile, catalog, history, today)
        val recent = Signals.recentIds(history, today)
        val sessions = Signals.prior(history, today).take(6).sortedBy { it.date }
        val payload = buildString {
            append("{")
            field("today", json(today.toString()))
            field("blockIndex", block.toString())
            field("weekInBlock", Training.weekInBlock(profile, today).toString())
            field("deload", dose.deload.toString())
            field("focus", json(focus.name))
            field(
                "dose",
                "{\"repsLow\":${dose.repsLow},\"repsHigh\":${dose.repsHigh}," +
                    "\"restSeconds\":${dose.restSeconds},\"movementCount\":${dose.movementCount}," +
                    "\"sets\":${dose.sets}}",
            )
            field("profile", profileJson(profile, today))
            field("slots", slots.joinToString(prefix = "[", postfix = "]") { slotJson(it) })
            field("lockedAnchors", locks.joinToString(prefix = "[", postfix = "]") { lockJson(it) })
            field("bannedExtraIds", recent.joinToString(prefix = "[", postfix = "]") { json(it) })
            field("menu", catalog.eligible(profile).joinToString(prefix = "[", postfix = "]") { menuJson(it) })
            append("\"lastSessions\":")
            append(sessions.joinToString(prefix = "[", postfix = "]") { sessionJson(it, catalog) })
            append("}")
        }
        return CoachRequest(systemPrompt, payload)
    }

    private fun StringBuilder.field(name: String, value: String) {
        append("\"")
        append(name)
        append("\":")
        append(value)
        append(",")
    }

    private fun profileJson(profile: UserProfile, today: LocalDate): String {
        val limits = profile.limits.joinToString(prefix = "[", postfix = "]") { json(it.name) }
        val bells = Training.sortedOwned(profile).joinToString(prefix = "[", postfix = "]")
        return "{\"age\":${Training.age(profile, today)},\"sex\":${json(profile.sex.name)}," +
            "\"heightCm\":${profile.heightCm},\"weightKg\":${profile.weightKg}," +
            "\"experience\":${json(profile.experience.name)},\"goal\":${json(profile.goal.name)}," +
            "\"daysPerWeek\":${profile.daysPerWeek},\"minutes\":${profile.minutesPerSession}," +
            "\"hasBench\":${profile.hasBench},\"limits\":$limits,\"dumbbellKg\":$bells}"
    }

    private fun slotJson(slot: Slot): String {
        val patterns = slot.patterns.joinToString(prefix = "[", postfix = "]") { json(it.name) }
        return "{\"anchor\":${slot.anchor},\"patterns\":$patterns}"
    }

    private fun lockJson(lock: AnchorLock): String =
        "{\"pattern\":${json(lock.pattern.name)},\"exerciseId\":${json(lock.exerciseId)}}"

    private fun menuJson(exercise: ProgrammedExercise): String {
        val muscles = exercise.primaryMuscles.joinToString(prefix = "[", postfix = "]") { json(it) }
        return "{\"id\":${json(exercise.id)},\"name\":${json(exercise.name)}," +
            "\"pattern\":${json(exercise.pattern.name)},\"familyId\":${json(exercise.familyId)}," +
            "\"tier\":${exercise.tier},\"loadType\":${json(exercise.loadType.name)}," +
            "\"needsBench\":${exercise.needsBench},\"muscles\":$muscles}"
    }

    private fun sessionJson(session: SessionRecord, catalog: Catalog): String {
        val adjustments = session.adjustments.joinToString(prefix = "[", postfix = "]") { adjustment ->
            "{\"exerciseId\":${json(adjustment.exerciseId)},\"familyId\":${json(adjustment.familyId)}," +
                "\"direction\":${json(adjustment.direction.name)}}"
        }
        val exercises = session.exercises.joinToString(prefix = "[", postfix = "]") { logged ->
            val name = catalog.find(logged.exerciseId)?.name ?: logged.exerciseId
            val done = logged.sets.filter { it.completed }
            val best = done.maxOfOrNull { it.reps } ?: 0
            "{\"id\":${json(logged.exerciseId)},\"name\":${json(name)},\"anchor\":${logged.anchor}," +
                "\"repsLow\":${logged.repsLow},\"repsHigh\":${logged.repsHigh}," +
                "\"prescribedLoadKg\":${logged.prescribedLoadKg ?: "null"}," +
                "\"completedSets\":${done.size},\"bestReps\":$best," +
                "\"hitTop\":${logged.hitTop()},\"missed\":${logged.missed()}}"
        }
        return "{\"date\":${json(session.date.toString())},\"rating\":${json(session.rating?.name ?: "UNFINISHED")}," +
            "\"focus\":${json(session.focus.name)},\"adjustments\":$adjustments,\"exercises\":$exercises}"
    }

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}
