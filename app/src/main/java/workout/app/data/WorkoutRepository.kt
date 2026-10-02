package workout.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import workout.app.coach.CoachClient
import workout.domain.AdjustmentDirection
import workout.domain.AdjustmentResult
import workout.domain.Catalog
import workout.domain.Planner
import workout.domain.PlanSource
import workout.domain.SessionPlan
import workout.domain.SessionRating
import workout.domain.SessionRecord
import workout.domain.Training
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

data class BestLift(val name: String, val detail: String)

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

    suspend fun today(): TodaySession = withContext(Dispatchers.IO) {
        val profile = dao.profile()?.toProfile() ?: error("Profile missing")
        val today = LocalDate.now()
        val existing = dao.session(today.toString())
        if (existing != null) {
            return@withContext TodaySession(
                plan = parsePlan(existing.planJson),
                headline = headline(existing.source, apiKey()),
                completed = existing.completed,
                rating = existing.rating?.let { SessionRating.valueOf(it) },
            )
        }
        val history = historyRecords(today)
        val built = build(profile, history, today)
        dao.saveSession(
            SessionEntity(
                date = today.toString(),
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

    suspend fun bestLifts(): List<BestLift> = withContext(Dispatchers.IO) {
        val best = linkedMapOf<String, BestLift>()
        dao.sessions().filter { it.completed }.forEach { entity ->
            val record = sessionRecord(entity, dao.sets(entity.date), emptyList())
            record.exercises.filter { it.anchor }.forEach { logged ->
                val done = logged.sets.filter { it.completed }
                if (done.isEmpty()) return@forEach
                val name = catalog.find(logged.exerciseId)?.name ?: logged.exerciseId
                val topReps = done.maxOf { it.reps }
                val load = logged.prescribedLoadKg
                val detail = if (load == null) "$topReps reps" else "${trim(load)} kg × $topReps"
                val previous = best[logged.exerciseId]
                val better = previous == null || (load ?: 0.0) > loadOf(previous) ||
                    ((load ?: 0.0) == loadOf(previous) && topReps > repsOf(previous))
                if (better) best[logged.exerciseId] = BestLift(name, detail)
            }
        }
        best.values.toList()
    }

    private suspend fun build(profile: UserProfile, history: List<SessionRecord>, today: LocalDate): TodaySession {
        val fallback = Planner.fallbackPlan(profile, catalog, history, today)
        val key = apiKey()
        if (key.isBlank()) {
            return TodaySession(fallback, appContext.getString(workout.app.R.string.coach_needs_key))
        }
        val prompt = appContext.assets.open("coach_prompt.md").bufferedReader().use { it.readText() }
        val request = Planner.prepareCoachRequest(prompt, profile, catalog, history, today)
        val first = runCatching { coach.propose(key, request, profile, catalog, today) }.getOrNull()
            ?.copy(focus = Training.focus(profile, history, today))
        val accepted = when {
            first == null -> null
            Planner.checkPlan(first, profile, catalog, history, today).isEmpty() -> first
            else -> {
                val reasons = Planner.checkPlan(first, profile, catalog, history, today).map { it.message }
                val repaired = runCatching {
                    coach.propose(key, request, profile, catalog, today, reasons)
                }.getOrNull()?.copy(focus = Training.focus(profile, history, today))
                repaired?.takeIf { Planner.checkPlan(it, profile, catalog, history, today).isEmpty() }
            }
        }
        return if (accepted != null) {
            TodaySession(accepted, "")
        } else {
            TodaySession(fallback, appContext.getString(workout.app.R.string.coach_unavailable))
        }
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

    private fun loadOf(lift: BestLift): Double = lift.detail.substringBefore(" kg").toDoubleOrNull() ?: 0.0

    private fun repsOf(lift: BestLift): Int = lift.detail.substringAfterLast("× ").trim().substringBefore(" ").toIntOrNull() ?: 0
}

data class SetDraft(val reps: Int, val loadKg: Double?, val done: Boolean)
