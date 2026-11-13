package workout.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import workout.app.coach.CoachClient
import workout.domain.AdjustmentDirection
import workout.domain.AdjustmentResult
import workout.domain.Catalog
import workout.domain.MuscleLine
import workout.domain.Planner
import workout.domain.PlannedExercise
import workout.domain.PlanSource
import workout.domain.SessionPlan
import workout.domain.SessionRating
import workout.domain.SessionRecord
import workout.domain.Training
import workout.domain.TrainingProgress
import workout.domain.UserProfile
import java.time.LocalDate

data class TodaySession(
    val plan: SessionPlan,
    val headline: String,
    val completed: Boolean = false,
    val rating: SessionRating? = null,
)

data class HistoryEntry(
    val date: LocalDate,
    val rating: SessionRating?,
    val note: String,
    val lines: List<String>,
)

class WorkoutRepository(
    context: Context,
    private val accountId: String,
    private val databaseName: String,
) {
    private val appContext = context.applicationContext
    private val dao = WorkoutDatabase.create(appContext, databaseName).dao()
    private val keys = KeyStore(appContext)
    private val coach = CoachClient()
    private val drive = DriveClient()
    val catalog: Catalog = loadCatalog(appContext)

    suspend fun profile(): UserProfile? = withContext(Dispatchers.IO) { dao.profile()?.toProfile() }

    suspend fun saveProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        val existing = dao.profile()
        val stored = if (existing == null) {
            profile
        } else {
            profile.copy(trainingStart = LocalDate.parse(existing.trainingStart))
        }
        dao.saveProfile(stored.toEntity())
        val today = LocalDate.now().toString()
        val open = dao.session(today)
        if (open != null && !open.completed) {
            dao.deleteOpenSession(today)
            dao.deleteSets(today)
            dao.deleteAdjustments(today)
        }
    }

    fun apiKey(): String {
        val named = keys.read(accountId)
        if (named.isNotEmpty()) return named
        val legacy = keys.readLegacy()
        if (legacy.isEmpty()) return ""
        keys.write(accountId, legacy)
        return legacy
    }

    fun saveApiKey(value: String) = keys.write(accountId, value)

    suspend fun today(): TodaySession? = withContext(Dispatchers.IO) {
        dao.profile()?.toProfile() ?: error("Profile missing")
        val existing = dao.session(LocalDate.now().toString()) ?: return@withContext null
        toToday(existing)
    }

    suspend fun upcoming(): TodaySession? = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        val existing = dao.sessions()
            .filter { it.date > today && !it.completed }
            .minByOrNull { it.date }
            ?: return@withContext null
        toToday(existing)
    }

    suspend fun generate(request: String, upcoming: Boolean): TodaySession? = withContext(Dispatchers.IO) {
        val asked = request.trim()
        if (asked.isEmpty()) error("Describe the workout first.")
        val profile = dao.profile()?.toProfile() ?: error("Profile missing")
        if (apiKey().isBlank()) return@withContext null
        val today = LocalDate.now()
        val sessions = dao.sessions()
        val todaySession = sessions.find { it.date == today.toString() }
        val date = if (upcoming || todaySession?.completed == true) {
            nextOpenDate(today, sessions)
        } else {
            today
        }
        val existing = sessions.find { it.date == date.toString() }
        if (existing?.completed == true) error("That day already has a finished workout.")
        if (existing != null && dao.sets(date.toString()).any { it.completed }) {
            error("That workout is already started.")
        }
        if (existing != null) {
            dao.deleteOpenSession(date.toString())
            dao.deleteSets(date.toString())
            dao.deleteAdjustments(date.toString())
        }
        val built = build(profile, historyRecords(date), date, asked)
        dao.saveSession(
            SessionEntity(
                date = date.toString(),
                rating = null,
                source = built.plan.source.name,
                note = built.plan.note,
                focus = built.plan.focus.name,
                planJson = built.plan.toJson(),
                completed = false,
            ),
        )
        built
    }

    suspend fun discardUnstarted(date: LocalDate) = withContext(Dispatchers.IO) {
        val key = date.toString()
        val existing = dao.session(key) ?: return@withContext
        if (existing.completed) return@withContext
        if (dao.sets(key).any { it.completed }) return@withContext
        dao.deleteOpenSession(key)
        dao.deleteSets(key)
        dao.deleteAdjustments(key)
    }

    suspend fun adjust(
        exerciseId: String,
        direction: AdjustmentDirection,
        started: Set<String>,
    ): AdjustmentResult = withContext(Dispatchers.IO) {
        val profile = dao.profile()?.toProfile() ?: error("Profile missing")
        val today = LocalDate.now().toString()
        val entity = dao.session(today) ?: error("No session")
        val plan = parsePlan(entity.planJson)
        val result = Planner.applyAdjustment(plan, catalog, profile, exerciseId, direction, started)
        dao.saveSession(entity.copy(planJson = result.plan.toJson(), note = result.plan.note))
        if (result.adjustments.isNotEmpty()) {
            dao.saveAdjustments(
                result.adjustments.map {
                    AdjustmentEntity(
                        date = today,
                        exerciseId = it.exerciseId,
                        familyId = it.familyId,
                        direction = it.direction.name,
                    )
                },
            )
        }
        result
    }

    suspend fun adoptVersion(
        chosenExerciseId: String,
        replacingExerciseId: String? = null,
    ): AdjustmentResult = withContext(Dispatchers.IO) {
        val profile = dao.profile()?.toProfile() ?: error("Profile missing")
        val today = LocalDate.now().toString()
        val entity = dao.session(today) ?: error("No session")
        val plan = parsePlan(entity.planJson)
        val result = Planner.adoptVersion(plan, catalog, profile, chosenExerciseId, replacingExerciseId)
        if (result.adjustments.isEmpty()) return@withContext result
        dao.saveSession(entity.copy(planJson = result.plan.toJson()))
        val families = result.adjustments.map { it.familyId }.toSet()
        val kept = dao.adjustments(today).filter { it.familyId !in families }
        dao.deleteAdjustments(today)
        val rows = kept + result.adjustments.map {
            AdjustmentEntity(
                date = today,
                exerciseId = it.exerciseId,
                familyId = it.familyId,
                direction = it.direction.name,
            )
        }
        if (rows.isNotEmpty()) dao.saveAdjustments(rows)
        result
    }

    suspend fun replaceSets(plan: SessionPlan, drafts: List<List<SetDraft>>) = withContext(Dispatchers.IO) {
        val date = plan.date.toString()
        dao.deleteSets(date)
        val rows = drafts.flatMapIndexed { exerciseIndex, sets ->
            val exercise = plan.exercises.getOrNull(exerciseIndex) ?: return@flatMapIndexed emptyList()
            sets.mapIndexed { setIndex, draft ->
                SetEntity(date, exercise.exerciseId, setIndex, draft.reps, draft.loadKg, draft.done)
            }
        }
        if (rows.isNotEmpty()) dao.saveSets(rows)
    }

    suspend fun savedSets(plan: SessionPlan): List<List<SetDraft>> = withContext(Dispatchers.IO) {
        val rows = dao.sets(plan.date.toString()).groupBy { it.exerciseId }
        plan.exercises.map { planned ->
            val saved = rows[planned.exerciseId].orEmpty().sortedBy { it.setIndex }
            if (saved.isEmpty()) {
                List(planned.sets) { SetDraft(planned.repsLow, planned.loadKg, done = false) }
            } else {
                saved.map { SetDraft(it.reps, it.loadKg, it.completed) }
            }
        }
    }

    suspend fun finish(rating: SessionRating) = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        val entity = dao.session(today) ?: return@withContext
        dao.saveSession(entity.copy(rating = rating.name, completed = true))
    }

    suspend fun addToToday(exerciseId: String): SessionPlan? = editToday { plan, profile, catalog, _ ->
        Planner.addExercise(plan, profile, catalog, exerciseId)
    }

    suspend fun removeFromToday(exerciseId: String): SessionPlan? = editToday { plan, _, _, _ ->
        Planner.removeExercise(plan, exerciseId)
    }

    suspend fun restoreToToday(planned: PlannedExercise, index: Int): SessionPlan? = editToday { plan, _, _, _ ->
        Planner.restoreExercise(plan, planned, index)
    }

    suspend fun reorderToday(idsInOrder: List<String>): SessionPlan? = editToday { plan, _, _, _ ->
        Planner.reorderSession(plan, idsInOrder)
    }

    private suspend fun editToday(
        edit: (SessionPlan, UserProfile, Catalog, List<SessionRecord>) -> SessionPlan,
    ): SessionPlan? = withContext(Dispatchers.IO) {
        val profile = dao.profile()?.toProfile() ?: return@withContext null
        val today = LocalDate.now()
        val entity = dao.session(today.toString()) ?: return@withContext null
        val plan = parsePlan(entity.planJson)
        val edited = edit(plan, profile, catalog, historyRecords(today))
        if (edited.exercises == plan.exercises) return@withContext plan
        dao.saveSession(entity.copy(planJson = edited.toJson()))
        edited
    }

    suspend fun resetToday() = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        val entity = dao.session(today) ?: return@withContext
        dao.saveSession(entity.copy(rating = null, completed = false))
        dao.deleteSets(today)
        dao.deleteAdjustments(today)
    }

    suspend fun history(): List<HistoryEntry> = withContext(Dispatchers.IO) {
        dao.sessions().filter { it.completed }.sortedByDescending { it.date }.map { entity ->
            val record = sessionRecord(entity, dao.sets(entity.date), dao.adjustments(entity.date))
            HistoryEntry(
                date = record.date,
                rating = record.rating,
                note = record.note,
                lines = record.exercises.map { logged ->
                    val name = catalog.find(logged.exerciseId)?.name ?: logged.exerciseId
                    val done = logged.sets.filter { it.completed }
                    val load = logged.prescribedLoadKg?.let { " · ${trim(it)} kg" } ?: ""
                    if (done.isEmpty()) {
                        val reps = if (logged.repsLow == logged.repsHigh) {
                            logged.repsLow.toString()
                        } else {
                            "${logged.repsLow}–${logged.repsHigh}"
                        }
                        "$name$load · ${logged.prescribedSets} × $reps"
                    } else {
                        "$name$load × ${done.maxOf { it.reps }}"
                    }
                },
            )
        }
    }

    suspend fun progress(): List<MuscleLine> = withContext(Dispatchers.IO) {
        val records = dao.sessions().map { entity ->
            sessionRecord(entity, dao.sets(entity.date), emptyList())
        }
        TrainingProgress.lines(records) { id -> catalog.find(id)?.primaryMuscles.orEmpty() }
    }

    private suspend fun build(
        profile: UserProfile,
        history: List<SessionRecord>,
        today: LocalDate,
        athleteRequest: String,
    ): TodaySession {
        val fallback = Planner.fallbackPlan(profile, catalog, history, today)
        val key = apiKey()
        val prompt = appContext.assets.open("coach_prompt.md").bufferedReader().use { it.readText() }
        val request = Planner.prepareCoachRequest(prompt, profile, catalog, history, today, athleteRequest)
        val first = runCatching { coach.propose(key, request, profile, catalog, today) }.getOrNull()
            ?.let { shaped(it, profile, history, today, athleteRequest) }
        val accepted = when {
            first == null -> null
            Planner.checkPlan(first, profile, catalog, history, today, requested = true).isEmpty() -> first
            else -> {
                val reasons = Planner.checkPlan(first, profile, catalog, history, today, requested = true)
                    .map { it.message }
                val repaired = runCatching {
                    coach.propose(key, request, profile, catalog, today, reasons)
                }.getOrNull()?.let { shaped(it, profile, history, today, athleteRequest) }
                repaired?.takeIf {
                    Planner.checkPlan(it, profile, catalog, history, today, requested = true).isEmpty()
                }
            }
        }
        return if (accepted != null) {
            TodaySession(accepted, "")
        } else {
            TodaySession(fallback, appContext.getString(workout.app.R.string.coach_unavailable))
        }
    }

    private fun shaped(
        plan: SessionPlan,
        profile: UserProfile,
        history: List<SessionRecord>,
        today: LocalDate,
        athleteRequest: String,
    ): SessionPlan {
        if (athleteRequest.isBlank()) return plan.copy(focus = Training.focus(profile, history, today))
        val programmed = plan.exercises.mapNotNull { catalog.find(it.exerciseId) }
        val focus = if (programmed.isEmpty()) plan.focus else Training.focusFrom(programmed)
        return plan.copy(focus = focus)
    }

    private fun toToday(existing: SessionEntity): TodaySession = TodaySession(
        plan = parsePlan(existing.planJson),
        headline = headline(existing.source, apiKey()),
        completed = existing.completed,
        rating = existing.rating?.let { SessionRating.valueOf(it) },
    )

    private suspend fun nextOpenDate(today: LocalDate, sessions: List<SessionEntity>): LocalDate {
        val byDate = sessions.associateBy { it.date }
        var day = today.plusDays(1)
        repeat(14) {
            val existing = byDate[day.toString()]
            val started = existing != null && dao.sets(day.toString()).any { it.completed }
            if (existing == null || (!existing.completed && !started)) return day
            day = day.plusDays(1)
        }
        return day
    }

    private suspend fun historyRecords(today: LocalDate): List<SessionRecord> =
        dao.sessions().filter { it.date < today.toString() }.map { entity ->
            sessionRecord(entity, dao.sets(entity.date), dao.adjustments(entity.date))
        }

    private fun headline(source: String, key: String): String = when {
        source == PlanSource.COACH.name -> ""
        key.isBlank() -> appContext.getString(workout.app.R.string.coach_needs_key)
        else -> appContext.getString(workout.app.R.string.coach_unavailable)
    }

    suspend fun exportJson(includeSecret: Boolean, accountEmail: String?): String = withContext(Dispatchers.IO) {
        BackupCodec.export(
            profile = dao.profile(),
            sessions = dao.sessions(),
            sets = dao.allSets(),
            adjustments = dao.allAdjustments(),
            apiKey = if (includeSecret) apiKey() else null,
            accountEmail = accountEmail,
        )
    }

    suspend fun importJson(json: String) = withContext(Dispatchers.IO) {
        val snapshot = BackupCodec.parse(json)
        dao.replaceBackup(snapshot.profile, snapshot.sessions, snapshot.sets, snapshot.adjustments)
        snapshot.apiKey?.let { saveApiKey(it) }
    }

    suspend fun adoptOtherDatabaseIfEmpty(): Boolean = withContext(Dispatchers.IO) {
        if (dao.profile() != null) return@withContext false
        val dir = appContext.getDatabasePath(LEGACY_DB).parentFile ?: return@withContext false
        val names = dir.list()?.filter { name ->
            name.startsWith("workout-") && name.endsWith(".db") && !name.contains("-journal")
        }.orEmpty()
        for (name in names) {
            val other = WorkoutDatabase.create(appContext, name).dao()
            val stored = other.profile() ?: continue
            dao.replaceBackup(stored, other.sessions(), other.allSets(), other.allAdjustments())
            return@withContext true
        }
        false
    }

    suspend fun importLegacyIfPresent(): Boolean = withContext(Dispatchers.IO) {
        if (databaseName == LEGACY_DB) return@withContext false
        val file = appContext.getDatabasePath(LEGACY_DB)
        if (!file.exists()) return@withContext false
        val legacy = WorkoutDatabase.create(appContext, LEGACY_DB).dao()
        val profile = legacy.profile() ?: return@withContext false
        dao.replaceBackup(profile, legacy.sessions(), legacy.allSets(), legacy.allAdjustments())
        if (apiKey().isBlank()) {
            val oldKey = keys.readLegacy()
            if (oldKey.isNotBlank()) saveApiKey(oldKey)
        }
        true
    }

    suspend fun restoreDrive(token: String): DriveFetch = withContext(Dispatchers.IO) {
        val json = drive.download(token) ?: return@withContext DriveFetch.Empty
        val snapshot = runCatching { BackupCodec.parse(json) }.getOrElse {
            return@withContext DriveFetch.Failed(it.message ?: "The Drive backup could not be read.")
        }
        if (snapshot.profile == null && snapshot.sessions.isEmpty()) return@withContext DriveFetch.Empty
        dao.replaceBackup(snapshot.profile, snapshot.sessions, snapshot.sets, snapshot.adjustments)
        snapshot.apiKey?.let { saveApiKey(it) }
        DriveFetch.Restored
    }

    suspend fun uploadDrive(token: String, accountEmail: String) = withContext(Dispatchers.IO) {
        drive.upload(token, exportJson(includeSecret = true, accountEmail))
    }

    companion object {
        const val LEGACY_DB = "workout.db"
    }

    private fun trim(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

data class SetDraft(val reps: Int, val loadKg: Double?, val done: Boolean)
