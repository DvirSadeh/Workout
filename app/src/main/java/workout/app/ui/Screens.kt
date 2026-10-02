package workout.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import workout.app.R
import workout.app.data.SetDraft
import workout.domain.AdjustmentDirection
import workout.domain.DayFocus
import workout.domain.Experience
import workout.domain.Goal
import workout.domain.LimitTag
import workout.domain.LoadType
import workout.domain.SessionRating
import workout.domain.Sex
import kotlin.math.abs

@Composable
fun WorkoutApp(model: WorkoutViewModel) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.stageBackupFile(context, uri)
    }
    val import = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
    if (model.importReady) {
        ConfirmImport(model)
        return
    }
    when (val screen = model.screen) {
        Screen.Loading -> LoadingScreen(model.status ?: stringResource(R.string.building))
        Screen.Onboarding -> ProfileScreen(model, onboarding = true, import)
        Screen.Home -> HomeScreen(model)
        Screen.Player -> PlayerScreen(model)
        Screen.History -> HistoryScreen(model)
        Screen.Profile -> ProfileScreen(model, onboarding = false, import)
        is Screen.Exercise -> ExerciseScreen(model, screen.id)
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(message)
        }
    }
}

@Composable
private fun ConfirmImport(model: WorkoutViewModel) {
    ScreenFrame(title = stringResource(R.string.load_my_data)) {
        Text(stringResource(R.string.confirm_import))
        Button(onClick = model::confirmImport, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_replace))
        }
        TextButton(onClick = model::cancelImport) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun HomeScreen(model: WorkoutViewModel) {
    val session = model.today
    ScreenFrame(title = stringResource(R.string.app_name)) {
        if (model.busy || session == null) {
            if (model.busy) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(model.status ?: stringResource(R.string.building))
            } else {
                Text(model.status ?: stringResource(R.string.could_not_build))
                Button(onClick = model::retry) { Text(stringResource(R.string.retry)) }
            }
            return@ScreenFrame
        }
        val plan = session.plan
        Text(weekTitle(plan.deload, plan.weekInBlock, plan.focus), style = MaterialTheme.typography.titleMedium)
        if (session.headline.isNotBlank()) Text(session.headline)
        Text(plan.note)
        if (model.firstSession) Text(stringResource(R.string.first_session_hint))
        if (session.completed) {
            val rating = session.rating?.let { ratingLabel(it) } ?: ""
            Text(stringResource(R.string.done_today, rating))
        }
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.save_my_data))
                Text(stringResource(R.string.save_my_data_detail), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = model.saveMyData, onCheckedChange = model::onSaveMyData)
        }
        model.backupNote?.let { Text(it) }
        HorizontalDivider()
        Text(stringResource(R.string.main_lifts), style = MaterialTheme.typography.titleMedium)
        if (model.best.isEmpty()) {
            Text(stringResource(R.string.main_lifts_empty))
        } else {
            model.best.forEach { lift -> Text("${lift.name} · ${lift.detail}") }
        }
        Button(
            onClick = model::openPlayer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(if (session.completed) R.string.review_workout else R.string.start_workout),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = model::openHistory) { Text(stringResource(R.string.history)) }
            OutlinedButton(onClick = model::openProfile) { Text(stringResource(R.string.profile)) }
        }
    }
}

@Composable
private fun PlayerScreen(model: WorkoutViewModel) {
    val plan = model.today?.plan
    if (plan == null || plan.exercises.isEmpty()) {
        ScreenFrame(title = stringResource(R.string.app_name), onBack = model::back) {
            Text(stringResource(R.string.could_not_build))
        }
        return
    }
    val index = model.exerciseIndex.coerceIn(0, plan.exercises.lastIndex)
    val planned = plan.exercises[index]
    val exercise = model.catalog.find(planned.exerciseId)
    val row = model.sets.getOrNull(index).orEmpty()
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = model::previousExercise, enabled = index > 0) {
                    Text(stringResource(R.string.previous))
                }
                Text(
                    stringResource(R.string.exercise_count, index + 1, plan.exercises.size),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = model::nextExercise, enabled = index < plan.exercises.lastIndex) {
                    Text(stringResource(R.string.next))
                }
            }
            Text(exercise?.name ?: planned.exerciseId, style = MaterialTheme.typography.headlineSmall)
            if (planned.anchor) {
                Text(stringResource(R.string.main_lift_badge), color = MaterialTheme.colorScheme.primary)
            }
            ExercisePhotos(exercise?.imageFiles.orEmpty())
            Text(
                stringResource(R.string.prescription, planned.sets, prescription(planned)) +
                    loadSuffix(planned.loadKg, exercise?.loadType),
            )
            if (planned.reason.isNotBlank()) Text(planned.reason)
            if (exercise?.loadType == LoadType.DUMBBELL) {
                DumbbellPicker(
                    owned = model.profile?.dumbbellKg.orEmpty(),
                    selected = row.firstOrNull { !it.done }?.loadKg ?: row.firstOrNull()?.loadKg,
                    onSelect = model::selectLoad,
                )
            }
            row.forEachIndexed { setIndex, draft ->
                SetRow(
                    number = setIndex + 1,
                    draft = draft,
                    onLess = { model.changeReps(setIndex, -1) },
                    onMore = { model.changeReps(setIndex, 1) },
                    onDone = { model.toggleDone(setIndex) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.adjust(AdjustmentDirection.EASIER) }) {
                    Text(stringResource(R.string.easier))
                }
                OutlinedButton(onClick = { model.adjust(AdjustmentDirection.HARDER) }) {
                    Text(stringResource(R.string.harder))
                }
            }
            if (model.coachMessage.isNotBlank()) Text(model.coachMessage)
            TextButton(onClick = model::openHowTo) { Text(stringResource(R.string.how_to)) }
            if (model.restRemaining > 0) {
                Text(
                    stringResource(R.string.rest_for, model.restRemaining),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = model::skipRest) { Text(stringResource(R.string.skip_rest)) }
            }
            if (model.askRating) {
                Text(stringResource(R.string.rate_prompt), style = MaterialTheme.typography.titleMedium)
                SessionRating.entries.forEach { rating ->
                    Button(onClick = { model.rate(rating) }, modifier = Modifier.fillMaxWidth()) {
                        Text(ratingLabel(rating))
                    }
                }
                TextButton(onClick = model::dismissRating) { Text(stringResource(R.string.back)) }
            } else {
                Button(onClick = model::requestFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.finish))
                }
            }
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SetRow(
    number: Int,
    draft: SetDraft,
    onLess: () -> Unit,
    onMore: () -> Unit,
    onDone: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.set_label, number), modifier = Modifier.weight(1f))
        TextButton(onClick = onLess) { Text("−") }
        Text(draft.reps.toString(), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onMore) { Text("+") }
        if (draft.done) {
            Button(onClick = onDone) { Text(stringResource(R.string.done)) }
        } else {
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.done)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DumbbellPicker(owned: List<Double>, selected: Double?, onSelect: (Double) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        owned.forEach { kilograms ->
            val picked = selected != null && abs(selected - kilograms) < 0.05
            if (picked) {
                Button(onClick = { onSelect(kilograms) }) { Text("${trimKg(kilograms)} kg") }
            } else {
                OutlinedButton(onClick = { onSelect(kilograms) }) { Text("${trimKg(kilograms)} kg") }
            }
        }
    }
}

@Composable
private fun HistoryScreen(model: WorkoutViewModel) {
    ScreenFrame(title = stringResource(R.string.history), onBack = model::back) {
        if (model.history.isEmpty()) {
            Text(stringResource(R.string.history_empty))
        }
        model.history.forEach { entry ->
            HorizontalDivider()
            Text(entry.date.toString(), style = MaterialTheme.typography.titleMedium)
            entry.rating?.let { Text(ratingLabel(it)) }
            if (entry.note.isNotBlank()) Text(entry.note)
            entry.lines.forEach { line -> Text(line) }
        }
    }
}

@Composable
private fun ExerciseScreen(model: WorkoutViewModel, id: String) {
    val exercise = model.catalog.find(id)
    ScreenFrame(title = exercise?.name ?: id, onBack = model::back) {
        if (exercise == null) return@ScreenFrame
        ExercisePhotos(exercise.imageFiles)
        if (exercise.primaryMuscles.isNotEmpty()) {
            Text(exercise.primaryMuscles.joinToString(", "))
        }
        Text(stringResource(R.string.how_to_title), style = MaterialTheme.typography.titleMedium)
        exercise.instructions.forEachIndexed { index, step ->
            Text("${index + 1}. $step")
        }
        val family = model.catalog.inFamily(exercise.familyId)
        val easier = family.filter { it.tier < exercise.tier }
        val harder = family.filter { it.tier > exercise.tier }
        if (easier.isNotEmpty()) {
            Text(stringResource(R.string.easier_versions), style = MaterialTheme.typography.titleMedium)
            easier.forEach { other ->
                TextButton(onClick = { model.openExercise(other.id) }) { Text(other.name) }
            }
        }
        if (harder.isNotEmpty()) {
            Text(stringResource(R.string.harder_versions), style = MaterialTheme.typography.titleMedium)
            harder.forEach { other ->
                TextButton(onClick = { model.openExercise(other.id) }) { Text(other.name) }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    model: WorkoutViewModel,
    onboarding: Boolean,
    onImport: () -> Unit,
) {
    val form = model.form
    ScreenFrame(
        title = stringResource(if (onboarding) R.string.app_name else R.string.profile),
        onBack = if (onboarding) null else model::back,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.save_my_data), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.save_my_data_detail), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = model.saveMyData, onCheckedChange = model::onSaveMyData)
        }
        model.backupNote?.let { Text(it) }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.load_my_data))
        }
        if (onboarding) Text(stringResource(R.string.disclaimer))
        OutlinedTextField(
            value = form.age,
            onValueChange = { model.updateForm(form.copy(age = it)) },
            label = { Text(stringResource(R.string.age)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(stringResource(R.string.sex))
        ChoiceRow(
            options = Sex.entries,
            selected = form.sex,
            label = { sexLabel(it) },
            onSelect = { model.updateForm(form.copy(sex = it)) },
        )
        OutlinedTextField(
            value = form.height,
            onValueChange = { model.updateForm(form.copy(height = it)) },
            label = { Text(stringResource(R.string.height)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = form.weight,
            onValueChange = { model.updateForm(form.copy(weight = it)) },
            label = { Text(stringResource(R.string.weight)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(stringResource(R.string.experience))
        ChoiceRow(
            options = Experience.entries,
            selected = form.experience,
            label = { experienceLabel(it) },
            onSelect = { model.updateForm(form.copy(experience = it)) },
        )
        Text(stringResource(R.string.goal))
        ChoiceRow(
            options = Goal.entries,
            selected = form.goal,
            label = { goalLabel(it) },
            onSelect = { model.updateForm(form.copy(goal = it)) },
        )
        Text(stringResource(R.string.days_per_week))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { model.updateForm(form.copy(days = (form.days - 1).coerceAtLeast(2))) }) { Text("−") }
            Text(form.days.toString(), modifier = Modifier.padding(horizontal = 12.dp))
            TextButton(onClick = { model.updateForm(form.copy(days = (form.days + 1).coerceAtMost(5))) }) { Text("+") }
        }
        Text(stringResource(R.string.minutes))
        ChoiceRow(
            options = listOf(20, 30, 45, 60),
            selected = form.minutes,
            label = { stringResource(R.string.minutes_value, it) },
            onSelect = { model.updateForm(form.copy(minutes = it)) },
        )
        OutlinedTextField(
            value = form.dumbbells,
            onValueChange = { model.updateForm(form.copy(dumbbells = it)) },
            label = { Text(stringResource(R.string.dumbbells)) },
            placeholder = { Text(stringResource(R.string.dumbbells_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(stringResource(R.string.pullup_note), style = MaterialTheme.typography.bodySmall)
        if (form.hasBench) {
            Button(onClick = { model.updateForm(form.copy(hasBench = false)) }) {
                Text(stringResource(R.string.has_bench))
            }
        } else {
            OutlinedButton(onClick = { model.updateForm(form.copy(hasBench = true)) }) {
                Text(stringResource(R.string.has_bench))
            }
        }
        Text(stringResource(R.string.limits))
        ChoiceRow(
            options = LimitTag.entries,
            selected = null,
            label = { limitLabel(it) },
            chosen = { it in form.limits },
            onSelect = { tag ->
                val next = form.limits.toMutableSet()
                if (!next.add(tag)) next.remove(tag)
                model.updateForm(form.copy(limits = next))
            },
        )
        OutlinedTextField(
            value = form.apiKey,
            onValueChange = { model.updateForm(form.copy(apiKey = it)) },
            label = { Text(stringResource(R.string.api_key)) },
            supportingText = { Text(stringResource(R.string.api_key_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        model.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = model::saveForm, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (onboarding) R.string.get_started else R.string.save))
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    chosen: (T) -> Boolean = { it == selected },
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val text = label(option)
            if (chosen(option)) {
                Button(onClick = { onSelect(option) }) { Text(text) }
            } else {
                OutlinedButton(onClick = { onSelect(option) }) { Text(text) }
            }
        }
    }
}

@Composable
private fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(title, style = MaterialTheme.typography.headlineMedium)
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExercisePhotos(files: List<String>) {
    val context = LocalContext.current
    val bitmaps by produceState(initialValue = emptyList<ImageBitmap>(), files) {
        value = withContext(Dispatchers.IO) {
            files.mapNotNull { path ->
                runCatching {
                    context.assets.open(path).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    var frame by remember(files) { mutableIntStateOf(0) }
    LaunchedEffect(bitmaps.size) {
        if (bitmaps.size > 1) {
            while (true) {
                delay(1_400)
                frame = (frame + 1) % bitmaps.size
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = bitmaps.getOrNull(frame), label = "exercise-photo") { image ->
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = stringResource(R.string.photo),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun weekTitle(deload: Boolean, weekInBlock: Int, focus: DayFocus): String {
    val name = focusLabel(focus)
    return if (deload) {
        stringResource(R.string.recovery_week, name)
    } else {
        stringResource(R.string.week_of_block, weekInBlock + 1, name)
    }
}

@Composable
private fun loadSuffix(loadKg: Double?, loadType: LoadType?): String = when {
    loadKg != null -> " · ${trimKg(loadKg)} kg"
    loadType == LoadType.BODYWEIGHT -> " · ${stringResource(R.string.bodyweight)}"
    else -> ""
}

@Composable
private fun sexLabel(sex: Sex): String = when (sex) {
    Sex.FEMALE -> stringResource(R.string.female)
    Sex.MALE -> stringResource(R.string.male)
    Sex.UNSPECIFIED -> stringResource(R.string.unspecified)
}

@Composable
private fun experienceLabel(experience: Experience): String = when (experience) {
    Experience.NEW -> stringResource(R.string.experience_new)
    Experience.SOME -> stringResource(R.string.experience_some)
    Experience.REGULAR -> stringResource(R.string.experience_regular)
}

@Composable
private fun goalLabel(goal: Goal): String = when (goal) {
    Goal.FIT -> stringResource(R.string.goal_fit)
    Goal.FAT_LOSS -> stringResource(R.string.goal_fat_loss)
    Goal.MUSCLE -> stringResource(R.string.goal_muscle)
    Goal.STRENGTH -> stringResource(R.string.goal_strength)
}

@Composable
private fun limitLabel(limit: LimitTag): String = when (limit) {
    LimitTag.KNEES -> stringResource(R.string.limit_knees)
    LimitTag.SHOULDERS -> stringResource(R.string.limit_shoulders)
    LimitTag.LOWER_BACK -> stringResource(R.string.limit_back)
    LimitTag.WRISTS -> stringResource(R.string.limit_wrists)
}

@Composable
private fun ratingLabel(rating: SessionRating): String = when (rating) {
    SessionRating.TOO_EASY -> stringResource(R.string.too_easy)
    SessionRating.JUST_RIGHT -> stringResource(R.string.just_right)
    SessionRating.HARD_BUT_GOOD -> stringResource(R.string.hard_but_good)
    SessionRating.TOO_HARD -> stringResource(R.string.too_hard)
}

@Composable
private fun focusLabel(focus: DayFocus): String = when (focus) {
    DayFocus.FULL_BODY -> stringResource(R.string.focus_full)
    DayFocus.UPPER -> stringResource(R.string.focus_upper)
    DayFocus.LOWER -> stringResource(R.string.focus_lower)
}
