package workout.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import workout.app.R
import workout.app.data.BackupCodec
import workout.app.data.BestLift
import workout.app.data.DownloadsBackup
import workout.app.data.HistoryEntry
import workout.app.data.SetDraft
import workout.app.data.TodaySession
import workout.app.data.WorkoutRepository
import workout.app.data.loadCatalog
import workout.domain.AdjustmentDirection
import workout.domain.Experience
import workout.domain.Goal
import workout.domain.LimitTag
import workout.domain.PlannedExercise
import workout.domain.SessionPlan
import workout.domain.SessionRating
import workout.domain.Sex
import workout.domain.UserProfile
import workout.domain.WorkoutSize
import java.time.LocalDate

sealed interface Screen {
    data object Loading : Screen
    data object Onboarding : Screen
    data object Home : Screen
    data object Player : Screen
    data object History : Screen
    data class PastSession(val date: LocalDate) : Screen
    data object Profile : Screen
    data class Exercise(val id: String) : Screen
}

data class ProfileForm(
    val age: String = "",
    val sex: Sex = Sex.UNSPECIFIED,
    val height: String = "",
    val weight: String = "",
    val experience: Experience = Experience.NEW,
    val goal: Goal = Goal.FIT,
    val days: Int = 3,
    val minutes: Int = 45,
    val dumbbells: String = "",
    val hasBench: Boolean = false,
    val limits: Set<LimitTag> = emptySet(),
    val apiKey: String = "",
)

data class PendingRemoval(
    val name: String,
    val planned: PlannedExercise,
    val index: Int,
    val sets: List<SetDraft>,
)

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {
    private val downloads = DownloadsBackup(app)
    private lateinit var repository: WorkoutRepository
    private val gate = Mutex()
    private val backStack = ArrayDeque<Screen>()
    private var restJob: Job? = null
    private var setsDirty = false
    private var adjusting = false
    private var backupJob: Job? = null
    private var undoJob: Job? = null
    private var stagedImport: String? = null

    val catalog = loadCatalog(app)

    var screen by mutableStateOf<Screen>(Screen.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var today by mutableStateOf<TodaySession?>(null)
        private set
    var profile by mutableStateOf<UserProfile?>(null)
        private set
    var best by mutableStateOf<List<BestLift>>(emptyList())
        private set
    var history by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set
    var firstSession by mutableStateOf(true)
        private set
    var exerciseIndex by mutableIntStateOf(0)
        private set
    var sets by mutableStateOf<List<List<SetDraft>>>(emptyList())
        private set
    var coachMessage by mutableStateOf("")
        private set
    var restRemaining by mutableIntStateOf(0)
        private set
    var askRating by mutableStateOf(false)
        private set
    var form by mutableStateOf(ProfileForm())
        private set
    var formError by mutableStateOf<String?>(null)
        private set
    var saveMyData by mutableStateOf(downloads.enabled())
        private set
    var backupNote by mutableStateOf<String?>(null)
        private set
    var importReady by mutableStateOf(false)
        private set
    var todayPlanOpen by mutableStateOf(false)
        private set
    var pendingRemoval by mutableStateOf<PendingRemoval?>(null)
        private set

    init {
        viewModelScope.launch {
            repository = WorkoutRepository(app, "local", WorkoutRepository.LEGACY_DB)
            if (repository.profile() == null) repository.adoptOtherDatabaseIfEmpty()
            openLocal()
        }
    }

    fun onSaveMyData(enabled: Boolean) {
        saveMyData = enabled
        downloads.setEnabled(enabled)
        backupNote = null
        if (enabled) {
            viewModelScope.launch { writeDownloads() }
        }
    }

    fun stageBackupFile(context: Context, uri: Uri) {
        if (!::repository.isInitialized) return
        viewModelScope.launch {
            val json = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }
            }.getOrNull()
            val snapshot = json?.let { runCatching { BackupCodec.parse(it) }.getOrNull() }
            if (json == null || snapshot == null) {
                backupNote = getApplication<Application>().getString(R.string.invalid_backup)
                return@launch
            }
            stagedImport = json
            importReady = true
        }
    }

    fun cancelImport() {
        stagedImport = null
        importReady = false
    }

    fun confirmImport() {
        val json = stagedImport ?: return
        viewModelScope.launch {
            busy = true
            val imported = runCatching { repository.importJson(json) }
            stagedImport = null
            importReady = false
            if (imported.isFailure) {
                busy = false
                backupNote = imported.exceptionOrNull()?.message
                return@launch
            }
            openLocal()
            scheduleBackup()
        }
    }

    fun updateForm(next: ProfileForm) {
        form = next
        formError = null
    }

    fun saveForm() {
        val parsed = validate() ?: return
        viewModelScope.launch {
            busy = true
            val saved = runCatching {
                repository.saveApiKey(form.apiKey)
                repository.saveProfile(parsed)
            }
            if (saved.isFailure) {
                busy = false
                formError = saved.exceptionOrNull()?.message
                return@launch
            }
            loadHome(replaceStack = true)
            scheduleBackup()
        }
    }

    fun retry() {
        viewModelScope.launch { loadHome(replaceStack = false) }
    }

    fun openProfile() {
        viewModelScope.launch {
            repository.profile()?.let { applyProfile(it) }
            show(Screen.Profile)
        }
    }

    fun openHistory() {
        viewModelScope.launch {
            history = repository.history()
            show(Screen.History)
        }
    }

    fun openPastSession(date: LocalDate) {
        if (history.none { it.date == date }) return
        show(Screen.PastSession(date))
    }

    fun openPlayer() {
        val session = today ?: return
        viewModelScope.launch {
            val loaded = gate.withLock { repository.savedSets(session.plan) }
            sets = loaded
            val unfinished = loaded.indexOfFirst { row -> row.any { !it.done } }
            exerciseIndex = when {
                unfinished >= 0 -> unfinished
                loaded.isNotEmpty() -> loaded.lastIndex
                else -> 0
            }
            coachMessage = ""
            askRating = false
            show(Screen.Player)
        }
    }

    fun openHowTo() {
        val id = today?.plan?.exercises?.getOrNull(exerciseIndex)?.exerciseId ?: return
        show(Screen.Exercise(id))
    }

    fun openExercise(id: String) {
        if (screen is Screen.Exercise && backStack.isNotEmpty()) backStack.removeLast()
        show(Screen.Exercise(id))
    }

    val canGoBack: Boolean
        get() = backStack.size > 1

    fun back() {
        if (backStack.size <= 1) return
        backStack.removeLast()
        screen = backStack.last()
    }

    fun previousExercise() {
        if (exerciseIndex > 0) exerciseIndex -= 1
    }

    fun nextExercise() {
        val last = today?.plan?.exercises?.lastIndex ?: return
        if (exerciseIndex < last) exerciseIndex += 1
    }

    fun changeReps(setIndex: Int, delta: Int) {
        editSet(setIndex) { it.copy(reps = (it.reps + delta).coerceIn(1, 40)) }
    }

    fun toggleDone(setIndex: Int) {
        val row = sets.getOrNull(exerciseIndex) ?: return
        val current = row.getOrNull(setIndex) ?: return
        val nowDone = !current.done
        editSet(setIndex) { it.copy(done = nowDone) }
        if (nowDone && sets.getOrNull(exerciseIndex)?.any { !it.done } == true) {
            val rest = today?.plan?.exercises?.getOrNull(exerciseIndex)?.restSeconds ?: 0
            if (rest > 0) startRest(rest)
        }
    }

    fun selectLoad(kilograms: Double) {
        val next = sets.toMutableList()
        val row = next.getOrNull(exerciseIndex) ?: return
        next[exerciseIndex] = row.map { it.copy(loadKg = kilograms) }
        sets = next
        setsDirty = true
        enqueueSave()
        scheduleBackup()
    }

    fun useThisToday(exerciseId: String) {
        if (adjusting) return
        val session = today ?: return
        adjusting = true
        viewModelScope.launch {
            try {
                gate.withLock {
                    val result = repository.adoptVersion(exerciseId)
                    if (result.adjustments.isEmpty()) return@withLock
                    val realigned = realign(sets, session.plan, result.plan)
                    today = session.copy(plan = result.plan)
                    sets = realigned
                    val slot = result.plan.exercises.indexOfFirst { it.exerciseId == exerciseId }
                    if (slot >= 0) exerciseIndex = slot
                    setsDirty = false
                    repository.replaceSets(result.plan, realigned)
                    if (screen is Screen.Exercise) openExercise(exerciseId)
                }
                scheduleBackup()
            } finally {
                adjusting = false
            }
        }
    }

    fun adjust(direction: AdjustmentDirection) {
        if (adjusting) return
        val session = today ?: return
        val planned = session.plan.exercises.getOrNull(exerciseIndex) ?: return
        val started = session.plan.exercises.mapIndexedNotNull { index, item ->
            if (index == exerciseIndex) return@mapIndexedNotNull null
            if (sets.getOrNull(index)?.any { it.done } == true) item.exerciseId else null
        }.toSet()
        adjusting = true
        viewModelScope.launch {
            try {
                gate.withLock {
                    val result = repository.adjust(planned.exerciseId, direction, started)
                    val realigned = realign(sets, session.plan, result.plan)
                    today = session.copy(plan = result.plan)
                    sets = realigned
                    coachMessage = result.message
                    exerciseIndex = exerciseIndex.coerceIn(0, result.plan.exercises.lastIndex.coerceAtLeast(0))
                    setsDirty = false
                    repository.replaceSets(result.plan, realigned)
                }
                scheduleBackup()
            } finally {
                adjusting = false
            }
        }
    }

    fun toggleTodayPlan() {
        todayPlanOpen = !todayPlanOpen
    }

    fun addExercise(exerciseId: String) = changeToday { repository.addToToday(exerciseId) }

    fun removeExercise(exerciseId: String) {
        if (adjusting) return
        val session = today ?: return
        val index = session.plan.exercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index < 0) return
        if (session.plan.exercises.size <= WorkoutSize.range(session.plan.focus).first) return
        pendingRemoval = PendingRemoval(
            name = catalog.find(exerciseId)?.name ?: exerciseId,
            planned = session.plan.exercises[index],
            index = index,
            sets = sets.getOrNull(index).orEmpty(),
        )
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            delay(5_000)
            pendingRemoval = null
        }
        changeToday { repository.removeFromToday(exerciseId) }
    }

    fun undoRemoval() {
        val pending = pendingRemoval ?: return
        pendingRemoval = null
        undoJob?.cancel()
        viewModelScope.launch {
            gate.withLock {
                val session = today ?: return@withLock
                if (session.plan.exercises.any { it.exerciseId == pending.planned.exerciseId }) return@withLock
                val edited = repository.restoreToToday(pending.planned, pending.index) ?: return@withLock
                if (edited.exercises == session.plan.exercises) return@withLock
                val realigned = realign(sets, session.plan, edited).toMutableList()
                val slot = edited.exercises.indexOfFirst { it.exerciseId == pending.planned.exerciseId }
                if (slot >= 0) {
                    val planned = edited.exercises[slot]
                    realigned[slot] = List(planned.sets) { setIndex ->
                        pending.sets.getOrNull(setIndex)
                            ?: SetDraft(planned.repsLow, planned.loadKg, done = false)
                    }
                }
                val currentId = session.plan.exercises.getOrNull(exerciseIndex)?.exerciseId
                today = session.copy(plan = edited)
                sets = realigned
                val kept = edited.exercises.indexOfFirst { it.exerciseId == currentId }
                exerciseIndex = if (kept >= 0) {
                    kept
                } else {
                    exerciseIndex.coerceIn(0, edited.exercises.lastIndex.coerceAtLeast(0))
                }
                setsDirty = false
                repository.replaceSets(edited, realigned)
            }
            scheduleBackup()
        }
    }

    fun reorderToday(idsInOrder: List<String>) = changeToday { repository.reorderToday(idsInOrder) }

    private fun changeToday(change: suspend () -> SessionPlan?) {
        if (adjusting) return
        val session = today ?: return
        adjusting = true
        viewModelScope.launch {
            try {
                gate.withLock {
                    val edited = change() ?: return@withLock
                    if (edited.exercises == session.plan.exercises) return@withLock
                    val currentId = session.plan.exercises.getOrNull(exerciseIndex)?.exerciseId
                    val realigned = realign(sets, session.plan, edited)
                    today = session.copy(plan = edited)
                    sets = realigned
                    val kept = edited.exercises.indexOfFirst { it.exerciseId == currentId }
                    exerciseIndex = if (kept >= 0) {
                        kept
                    } else {
                        exerciseIndex.coerceIn(0, edited.exercises.lastIndex.coerceAtLeast(0))
                    }
                    setsDirty = false
                    repository.replaceSets(edited, realigned)
                }
                scheduleBackup()
            } finally {
                adjusting = false
            }
        }
    }

    fun resetToday() {
        pendingRemoval = null
        undoJob?.cancel()
        val session = today ?: return
        viewModelScope.launch {
            gate.withLock {
                repository.resetToday()
                today = session.copy(completed = false, rating = null)
                sets = session.plan.exercises.map { planned ->
                    List(planned.sets) { SetDraft(planned.repsLow, planned.loadKg, done = false) }
                }
                restJob?.cancel()
                restRemaining = 0
                askRating = false
                coachMessage = ""
                exerciseIndex = 0
                setsDirty = false
            }
            history = repository.history()
            best = repository.bestLifts()
            firstSession = history.isEmpty()
            scheduleBackup()
        }
    }

    fun requestFinish() {
        askRating = true
    }

    fun dismissRating() {
        askRating = false
    }

    fun rate(rating: SessionRating) {
        if (!askRating) return
        askRating = false
        viewModelScope.launch {
            gate.withLock {
                val plan = today?.plan ?: return@withLock
                repository.replaceSets(plan, sets)
                repository.finish(rating)
                setsDirty = false
                askRating = false
                restJob?.cancel()
                restRemaining = 0
            }
            loadHome(replaceStack = true)
            scheduleBackup()
        }
    }

    fun skipRest() {
        restJob?.cancel()
        restRemaining = 0
    }

    private fun editSet(setIndex: Int, change: (SetDraft) -> SetDraft) {
        val next = sets.toMutableList()
        val row = next.getOrNull(exerciseIndex)?.toMutableList() ?: return
        val current = row.getOrNull(setIndex) ?: return
        row[setIndex] = change(current)
        next[exerciseIndex] = row
        sets = next
        setsDirty = true
        enqueueSave()
        scheduleBackup()
    }

    private fun enqueueSave() {
        viewModelScope.launch {
            gate.withLock {
                while (setsDirty) {
                    val plan = today?.plan ?: return@withLock
                    val snapshot = sets
                    setsDirty = false
                    repository.replaceSets(plan, snapshot)
                }
            }
        }
    }

    private fun startRest(seconds: Int) {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            restRemaining = seconds
            while (restRemaining > 0) {
                delay(1_000)
                restRemaining -= 1
            }
        }
    }

    private suspend fun loadHome(replaceStack: Boolean) {
        busy = true
        status = getApplication<Application>().getString(R.string.building)
        if (replaceStack) {
            backStack.clear()
            backStack.add(Screen.Home)
            screen = Screen.Home
        }
        val session = runCatching { repository.today() }
        if (session.isFailure) {
            busy = false
            status = session.exceptionOrNull()?.message
                ?: getApplication<Application>().getString(R.string.could_not_build)
            return
        }
        val built = session.getOrThrow()
        today = built
        profile = repository.profile()
        best = repository.bestLifts()
        history = repository.history()
        firstSession = history.isEmpty()
        sets = repository.savedSets(built.plan)
        busy = false
        status = null
    }

    private suspend fun resumeUnfinishedSession() {
        val session = today ?: return
        if (session.completed) return
        val loaded = gate.withLock { repository.savedSets(session.plan) }
        if (loaded.none { row -> row.any { it.done } }) return
        sets = loaded
        val unfinished = loaded.indexOfFirst { row -> row.any { !it.done } }
        exerciseIndex = if (unfinished >= 0) unfinished else loaded.lastIndex.coerceAtLeast(0)
        coachMessage = ""
        askRating = false
        show(Screen.Player)
    }

    private fun applyProfile(existing: UserProfile) {
        profile = existing
        val age = (LocalDate.now().year - existing.birthYear).coerceAtLeast(0)
        form = ProfileForm(
            age = age.toString(),
            sex = existing.sex,
            height = existing.heightCm.toString(),
            weight = trimKg(existing.weightKg),
            experience = existing.experience,
            goal = existing.goal,
            days = existing.daysPerWeek,
            minutes = existing.minutesPerSession,
            dumbbells = existing.dumbbellKg.joinToString(", ") { trimKg(it) },
            hasBench = existing.hasBench,
            limits = existing.limits,
            apiKey = repository.apiKey(),
        )
        formError = null
    }

    private fun validate(): UserProfile? {
        val resources = getApplication<Application>()
        val age = form.age.trim().toIntOrNull()
        if (age == null || age !in 14..90) {
            formError = resources.getString(R.string.error_age)
            return null
        }
        val height = form.height.trim().toIntOrNull()
        if (height == null || height !in 120..230) {
            formError = resources.getString(R.string.error_height)
            return null
        }
        val weight = form.weight.trim().replace(',', '.').toDoubleOrNull()
        if (weight == null || weight !in 35.0..250.0) {
            formError = resources.getString(R.string.error_weight)
            return null
        }
        val bells = parseBells(form.dumbbells)
        if (bells == null) {
            formError = resources.getString(R.string.error_dumbbells)
            return null
        }
        formError = null
        return UserProfile(
            birthYear = LocalDate.now().year - age,
            sex = form.sex,
            heightCm = height,
            weightKg = weight,
            experience = form.experience,
            goal = form.goal,
            daysPerWeek = form.days,
            minutesPerSession = form.minutes,
            dumbbellKg = bells,
            hasBench = form.hasBench,
            limits = form.limits,
            trainingStart = profile?.trainingStart ?: LocalDate.now(),
        )
    }

    private fun show(next: Screen) {
        backStack.add(next)
        screen = next
    }

    private fun realign(
        previous: List<List<SetDraft>>,
        before: SessionPlan,
        after: SessionPlan,
    ): List<List<SetDraft>> {
        val previousById = buildMap {
            before.exercises.forEachIndexed { index, planned ->
                if (planned.exerciseId !in this) put(planned.exerciseId, previous.getOrNull(index).orEmpty())
            }
        }
        return after.exercises.map { planned ->
            val oldSets = previousById[planned.exerciseId]
            if (oldSets == null) {
                List(planned.sets) { SetDraft(planned.repsLow, planned.loadKg, done = false) }
            } else {
                List(planned.sets) { setIndex ->
                    val old = oldSets.getOrNull(setIndex)
                    when {
                        old == null -> SetDraft(planned.repsLow, planned.loadKg, done = false)
                        old.done -> old
                        else -> old.copy(loadKg = planned.loadKg)
                    }
                }
            }
        }
    }

    private fun parseBells(text: String): List<Double>? {
        val parts = text.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val values = parts.map { token ->
            token.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it <= 100.0 } ?: return null
        }
        return values.distinct().sorted()
    }

    private suspend fun openLocal() {
        val existing = repository.profile()
        if (existing == null) {
            form = ProfileForm()
            backStack.clear()
            backStack.add(Screen.Onboarding)
            screen = Screen.Onboarding
            busy = false
            status = null
        } else {
            applyProfile(existing)
            loadHome(replaceStack = true)
            resumeUnfinishedSession()
        }
    }

    private fun scheduleBackup() {
        if (!saveMyData || !::repository.isInitialized) return
        backupJob?.cancel()
        backupJob = viewModelScope.launch {
            delay(2_000)
            writeDownloads()
        }
    }

    private suspend fun writeDownloads() {
        val resources = getApplication<Application>()
        val written = runCatching {
            val json = repository.exportJson(includeSecret = true, accountEmail = null)
            withContext(Dispatchers.IO) { downloads.write(json) }
        }
        backupNote = if (written.isSuccess) {
            resources.getString(R.string.saved_to_downloads)
        } else {
            written.exceptionOrNull()?.message ?: resources.getString(R.string.save_failed)
        }
    }
}

internal fun trimKg(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

internal fun prescription(planned: PlannedExercise): String {
    val reps = if (planned.repsLow == planned.repsHigh) {
        planned.repsLow.toString()
    } else {
        "${planned.repsLow}–${planned.repsHigh}"
    }
    return "$reps"
}
