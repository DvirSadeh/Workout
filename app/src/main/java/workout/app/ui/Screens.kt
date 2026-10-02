package workout.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import workout.app.R
import workout.app.data.TodaySession
import workout.domain.DayFocus
import workout.domain.Experience
import workout.domain.Goal
import workout.domain.LimitTag
import workout.domain.LoadType
import workout.domain.MuscleLine
import workout.domain.PlayMode
import workout.domain.PlayStep
import workout.domain.SessionRating
import workout.domain.Sex
import workout.domain.PlannedExercise
import workout.domain.ProgrammedExercise
import workout.domain.UserProfile
import workout.domain.WorkoutSize
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun WorkoutApp(model: WorkoutViewModel) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.stageBackupFile(context, uri)
    }
    val import = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
    BackHandler(enabled = model.importReady || model.canGoBack) {
        if (model.importReady) model.cancelImport() else model.back()
    }
    if (model.importReady) {
        ConfirmImport(model)
        return
    }
    if (model.screen != Screen.Loading && !model.guidanceAccepted) {
        GuidanceScreen(model::acceptGuidance)
        return
    }
    when (val screen = model.screen) {
        Screen.Loading -> LoadingScreen(model.status ?: stringResource(R.string.building))
        Screen.Onboarding -> ProfileScreen(model, onboarding = true, import)
        Screen.Home -> HomeScreen(model)
        Screen.Player -> PlayerScreen(model)
        Screen.History -> HistoryScreen(model)
        is Screen.PastSession -> PastSessionScreen(model, screen.date)
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
    val today = remember { LocalDate.now() }
    val dateText = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { RemovalUndoHost(model) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(dateText, style = MaterialTheme.typography.headlineSmall)
            }
            val session = model.today
            if (model.busy || session == null) {
                HomePending(model)
            } else {
                HomeReady(model, session, today)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomePending(model: WorkoutViewModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (model.busy) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(model.status ?: stringResource(R.string.building))
            } else {
                Text(model.status ?: stringResource(R.string.could_not_build))
                Button(onClick = model::retry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun HomeReady(model: WorkoutViewModel, session: TodaySession, today: LocalDate) {
    val plan = session.plan
    val started = model.sets.any { row -> row.any { it.done } }
    val doneSets = model.sets.sumOf { row -> row.count { it.done } }
    val totalSets = model.sets.sumOf { it.size }
    val action = when {
        session.completed -> R.string.review_workout
        started -> R.string.continue_workout
        else -> R.string.start_workout
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Column(
                Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            focusLabel(plan.focus),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(
                                R.string.session_meta,
                                sessionMinutes(plan.exercises.size),
                                plan.exercises.size,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (session.headline.isNotBlank()) SessionNote(session.headline)
                }
                Button(
                    onClick = model::openPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(action), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = model::toggleTodayPlan,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        stringResource(
                            if (model.todayPlanOpen) R.string.close_adjustments else R.string.adjust_today_plan,
                        ),
                    )
                }
                if (model.todayPlanOpen) {
                    if (session.completed) {
                        val rating = session.rating?.let { ratingLabel(it) } ?: ""
                        Text(
                            stringResource(R.string.done_today, rating),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else if (started && totalSets > 0) {
                        val nextIndex = model.sets.indexOfFirst { row -> row.any { !it.done } }
                        val nextName = plan.exercises.getOrNull(nextIndex)?.let { planned ->
                            model.catalog.find(planned.exerciseId)?.name
                        }
                        Text(stringResource(R.string.sets_progress, doneSets, totalSets))
                        LinearProgressIndicator(
                            progress = { doneSets.toFloat() / totalSets.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(99.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                        if (nextName != null) {
                            Text(
                                stringResource(R.string.next_exercise, nextName),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    AdjustableExercises(model, session)
                }
                if (session.completed || started) {
                    OutlinedButton(
                        onClick = model::resetToday,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(stringResource(R.string.reset_today))
                    }
                }
            }
        }
    }

    model.profile?.let { profile ->
        val weekStart = today.with(DayOfWeek.MONDAY)
        val doneThisWeek = model.history.count { entry ->
            !entry.date.isBefore(weekStart) && !entry.date.isAfter(today)
        }
        CalendarCard(
            profile = profile,
            blockIndex = plan.blockIndex,
            weekInBlock = plan.weekInBlock,
            deload = plan.deload,
            today = today,
            trained = model.history.map { it.date }.toSet(),
            doneThisWeek = doneThisWeek,
            onOpenDay = model::openPastSession,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.your_plan), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.plan_line,
                        goalLabel(profile.goal),
                        profile.daysPerWeek,
                        profile.minutesPerSession,
                    ),
                )
                val skipped = listOf(
                    LimitTag.KNEES to stringResource(R.string.limit_knees),
                    LimitTag.SHOULDERS to stringResource(R.string.limit_shoulders),
                    LimitTag.LOWER_BACK to stringResource(R.string.limit_back),
                    LimitTag.WRISTS to stringResource(R.string.limit_wrists),
                ).filter { (tag, _) -> tag in profile.limits }.joinToString(", ") { it.second }
                if (skipped.isNotEmpty()) {
                    Text(
                        stringResource(R.string.skipping_limits, skipped),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(
                    onClick = model::openProfile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.edit_plan))
                }
            }
        }
    }

    ProgressCard(model.progress)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.save_my_data), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.save_my_data_detail), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = model.saveMyData, onCheckedChange = model::onSaveMyData)
            }
            model.backupNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SessionNote(message: String) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.session_note)
    val density = LocalDensity.current
    Box {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { open = !open }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "i",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = with(density) { IntOffset(0, 48.dp.roundToPx()) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 280.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(
    profile: UserProfile,
    blockIndex: Int,
    weekInBlock: Int,
    deload: Boolean,
    today: LocalDate,
    trained: Set<LocalDate>,
    doneThisWeek: Int,
    onOpenDay: (LocalDate) -> Unit,
) {
    val blockStart = profile.trainingStart.plusDays(blockIndex.toLong() * 28)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val blockEnd = blockStart.plusDays(27)
            val monthPattern = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
            val months = if (blockStart.month == blockEnd.month) {
                blockStart.format(monthPattern)
            } else {
                "${blockStart.format(monthPattern)} – ${blockEnd.format(monthPattern)}"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (deload) {
                        stringResource(R.string.recovery_week_label)
                    } else {
                        stringResource(R.string.week_number, weekInBlock + 1)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(months, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WeekBars(weekInBlock)
            Row {
                repeat(7) { offset ->
                    val label = blockStart.plusDays(offset.toLong()).dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                        .take(2)
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            repeat(4) { week ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (week == weekInBlock) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                Color.Transparent
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(7) { day ->
                        val date = blockStart.plusDays(week * 7L + day)
                        val hasWorkout = date in trained
                        DayCell(
                            date = date,
                            isToday = date == today,
                            trained = hasWorkout,
                            onOpen = if (hasWorkout) {
                                { onOpenDay(date) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.week_sessions, doneThisWeek, profile.daysPerWeek.coerceAtLeast(1)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdjustableExercises(model: WorkoutViewModel, session: TodaySession) {
    val plan = session.plan
    val exercises = plan.exercises
    val limits = WorkoutSize.range(plan.focus)
    val canRemove = exercises.size > limits.first
    val canAdd = exercises.size < limits.last
    val profile = model.profile
    val choices = if (profile == null) {
        emptyList()
    } else {
        WorkoutSize.additionChoices(plan, profile, model.catalog)
    }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.toPx() }
    val slots = remember { mutableStateMapOf<String, Rect>() }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var liveIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewIds by remember { mutableStateOf<List<String>?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var ghostSize by remember { mutableStateOf(Size.Zero) }
    var addOpen by remember { mutableStateOf(false) }
    val committedIds = exercises.map { it.exerciseId }
    val displayIds = when {
        draggedId != null -> liveIds
        previewIds != null && previewIds != committedIds -> previewIds.orEmpty()
        else -> committedIds
    }
    val shownIds = remember { mutableStateOf(displayIds) }
    shownIds.value = displayIds
    SideEffect {
        val keep = displayIds.toSet()
        slots.keys.toList().filter { it !in keep }.forEach { slots.remove(it) }
        rowHeights.keys.toList().filter { it !in keep }.forEach { rowHeights.remove(it) }
    }
    LaunchedEffect(committedIds) {
        if (previewIds == committedIds) previewIds = null
    }
    val animatedYs = rememberAnimatedTops(
        ids = displayIds,
        heights = rowHeights,
        dragging = draggedId != null,
        gap = gapPx.roundToInt(),
    )

    if (addOpen && canAdd) {
        AddExerciseDialog(
            choices = choices,
            female = profile?.sex == Sex.FEMALE,
            onDismiss = { addOpen = false },
            onPick = { exerciseId ->
                addOpen = false
                model.addExercise(exerciseId)
            },
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Layout(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { gridOrigin = it.positionInRoot() }
                    .pointerInput(exercises) {
                        arrangeExercises(
                            exerciseAt = { position ->
                                val finger = gridOrigin + position
                                val id = slots.entries.firstOrNull { (_, rect) -> rect.contains(finger) }?.key
                                    ?: return@arrangeExercises null
                                exercises.indexOfFirst { it.exerciseId == id }.takeIf { it >= 0 }
                            },
                            onOpen = { index ->
                                model.openExercise(exercises[index].exerciseId)
                            },
                            onDragStart = { index, position ->
                                val id = exercises.getOrNull(index)?.exerciseId ?: return@arrangeExercises
                                draggedId = id
                                liveIds = shownIds.value
                                pointer = position
                                val rect = slots[id]
                                ghostSize = rect?.size ?: Size.Zero
                                val topLeft = rect?.topLeft?.minus(gridOrigin) ?: Offset.Zero
                                grabOffset = position - topLeft
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onMove = { position ->
                                pointer = position
                                val moving = draggedId ?: return@arrangeExercises
                                val ids = liveIds
                                val current = ids.indexOf(moving)
                                if (current < 0) return@arrangeExercises
                                val target = insertionIndex(
                                    fingerY = (gridOrigin + position).y,
                                    ids = ids,
                                    heights = rowHeights,
                                    originY = gridOrigin.y,
                                    gap = gapPx,
                                )
                                if (target == current) return@arrangeExercises
                                val next = ids.toMutableList()
                                next.removeAt(current)
                                next.add(target.coerceIn(0, next.size), moving)
                                liveIds = next
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDrop = {
                                val order = liveIds
                                val original = exercises.map { it.exerciseId }
                                draggedId = null
                                if (order.size == original.size && order.toSet() == original.toSet() && order != original) {
                                    previewIds = order
                                    model.reorderToday(order)
                                }
                            },
                            onCancel = { draggedId = null },
                        )
                    },
                content = {
                    displayIds.forEach { id ->
                        key(id) {
                            val planned = exercises.firstOrNull { it.exerciseId == id }
                            if (planned != null) {
                            val exercise = model.catalog.find(id)
                            val name = exercise?.name ?: id
                            val setIndex = exercises.indexOfFirst { it.exerciseId == id }
                            val finished = model.sets.getOrNull(setIndex).orEmpty().let { rowSets ->
                                rowSets.isNotEmpty() && rowSets.all { it.done }
                            }
                            ExerciseRow(
                                name = name,
                                detail = exerciseDetail(planned),
                                finished = finished,
                                imagePath = exercise?.imageFiles?.firstOrNull(),
                                muscles = exercise?.primaryMuscles.orEmpty(),
                                female = profile?.sex == Sex.FEMALE,
                                removeLabel = stringResource(R.string.remove_exercise, name),
                                canRemove = canRemove,
                                hidden = id == draggedId,
                                onRemove = { model.removeExercise(id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coords ->
                                        slots[id] = Rect(
                                            coords.positionInRoot(),
                                            Size(coords.size.width.toFloat(), coords.size.height.toFloat()),
                                        )
                                        if (rowHeights[id] != coords.size.height) rowHeights[id] = coords.size.height
                                    },
                            )
                            }
                        }
                    }
                },
            ) { measurables, constraints ->
                val gap = gapPx.roundToInt()
                val loose = constraints.copy(minHeight = 0)
                val placeables = measurables.map { it.measure(loose) }
                var cursor = 0
                val measuredTops = IntArray(placeables.size)
                placeables.forEachIndexed { index, placeable ->
                    measuredTops[index] = cursor
                    cursor += placeable.height
                    if (index != placeables.lastIndex) cursor += gap
                }
                val height = if (placeables.isEmpty()) 0 else cursor
                // Read during measure so releasing the finger always relayouts onto these slots.
                val draggingNow = draggedId != null
                val sliding = animatedYs
                layout(constraints.maxWidth, height) {
                    placeables.forEachIndexed { index, placeable ->
                        val id = displayIds.getOrNull(index)
                        val settled = measuredTops[index]
                        val y = if (draggingNow && id != null) sliding[id] ?: settled else settled
                        placeable.place(0, y)
                    }
                }
            }
            val movingId = draggedId
            val planned = exercises.firstOrNull { it.exerciseId == movingId }
            if (movingId != null && planned != null && ghostSize.width > 0f) {
                val exercise = model.catalog.find(movingId)
                val name = exercise?.name ?: movingId
                val setIndex = exercises.indexOfFirst { it.exerciseId == movingId }
                val finished = model.sets.getOrNull(setIndex).orEmpty().let { rowSets ->
                    rowSets.isNotEmpty() && rowSets.all { it.done }
                }
                val left = pointer.x - grabOffset.x
                val top = pointer.y - grabOffset.y
                ExerciseRow(
                    name = name,
                    detail = exerciseDetail(planned),
                    finished = finished,
                    imagePath = exercise?.imageFiles?.firstOrNull(),
                    muscles = exercise?.primaryMuscles.orEmpty(),
                    female = profile?.sex == Sex.FEMALE,
                    removeLabel = "",
                    canRemove = false,
                    hidden = false,
                    lifted = true,
                    onRemove = {},
                    modifier = Modifier.layout { measurable, _ ->
                        val width = ghostSize.width.roundToInt().coerceAtLeast(1)
                        val height = ghostSize.height.roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(0, 0) {
                            placeable.placeWithLayer(left.roundToInt(), top.roundToInt()) {
                                shadowElevation = with(density) { 12.dp.toPx() }
                                scaleX = 1.03f
                                scaleY = 1.03f
                            }
                        }
                    },
                )
            }
        }
        if (canAdd) {
            Spacer(Modifier.height(8.dp))
            AddExerciseTile(
                label = stringResource(R.string.add_exercise),
                onAdd = { addOpen = true },
            )
        }
    }
}

@Composable
private fun rememberAnimatedTops(
    ids: List<String>,
    heights: Map<String, Int>,
    dragging: Boolean,
    gap: Int,
): Map<String, Int> {
    val anims = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    val tops = runningTops(ids, heights, gap)
    LaunchedEffect(tops, dragging) {
        tops?.let { targets ->
            coroutineScope {
                ids.forEach { id ->
                    val target = targets.getValue(id)
                    val existing = anims[id]
                    if (existing == null) {
                        anims[id] = Animatable(target)
                    } else {
                        launch {
                            if (dragging && existing.value != target) {
                                existing.animateTo(target, rowSpring)
                            } else {
                                existing.snapTo(target)
                            }
                        }
                    }
                }
            }
        }
    }
    return buildMap {
        ids.forEach { id ->
            val value = anims[id]?.value
            if (value != null) put(id, value.roundToInt())
        }
    }
}

private val rowSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private fun runningTops(ids: List<String>, heights: Map<String, Int>, gap: Int): Map<String, Float>? {
    if (ids.isEmpty() || ids.any { heights[it] == null }) return null
    var y = 0
    return buildMap {
        ids.forEachIndexed { index, id ->
            put(id, y.toFloat())
            y += heights.getValue(id)
            if (index != ids.lastIndex) y += gap
        }
    }
}

private fun insertionIndex(
    fingerY: Float,
    ids: List<String>,
    heights: Map<String, Int>,
    originY: Float,
    gap: Float,
): Int {
    if (ids.isEmpty()) return 0
    var y = originY
    for (index in ids.indices) {
        val height = heights[ids[index]]?.toFloat() ?: return index
        if (fingerY < y + height / 2f) return index
        y += height + gap
    }
    return ids.lastIndex
}

@Composable
private fun ExerciseRow(
    name: String,
    detail: String,
    finished: Boolean,
    imagePath: String?,
    muscles: List<String>,
    female: Boolean,
    removeLabel: String,
    canRemove: Boolean,
    hidden: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
) {
    val muscleNote = if (muscles.isEmpty()) {
        null
    } else {
        stringResource(R.string.works_muscles, muscles.joinToString(", "))
    }
    Surface(
        modifier = modifier.alpha(if (hidden) 0f else 1f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(start = 6.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.background.copy(alpha = if (canRemove || lifted) 1f else 0.45f),
                    )
                    .then(
                        if (lifted) {
                            Modifier
                        } else {
                            Modifier.tapControl(enabled = canRemove, label = removeLabel, onTap = onRemove)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "−",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canRemove || lifted) 1f else 0.45f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    detail,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (finished) {
                    Text(
                        stringResource(R.string.done),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            ExerciseThumb(imagePath, Modifier.size(64.dp))
            BodyDiagram(
                muscles = muscles,
                female = female,
                modifier = Modifier.size(width = 72.dp, height = 84.dp),
                contentDescription = muscleNote,
            )
        }
    }
}

@Composable
private fun ExerciseThumb(path: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open(path).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun AddExerciseTile(
    label: String,
    onAdd: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .tapControl(enabled = true, label = label, onTap = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RemovalUndoHost(model: WorkoutViewModel) {
    val pending = model.pendingRemoval
    var retained by remember { mutableStateOf<PendingRemoval?>(null) }
    SideEffect {
        if (pending != null) retained = pending
    }
    val item = pending ?: retained
    AnimatedVisibility(
        visible = pending != null,
        modifier = Modifier.fillMaxWidth(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        if (item != null) {
            val until = model.undoUntil
            var fraction by remember(until) { mutableFloatStateOf(1f) }
            LaunchedEffect(until) {
                while (isActive) {
                    val left = until - SystemClock.elapsedRealtime()
                    fraction = (left.toFloat() / UndoWindowMillis).coerceIn(0f, 1f)
                    if (left <= 0L) break
                    withFrameNanos { }
                }
            }
            RemovalUndo(item.name, fraction, model::undoRemoval)
        }
    }
}

@Composable
private fun RemovalUndo(name: String, fraction: Float, onUndo: () -> Unit) {
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth(),
        shape = SnackbarDefaults.shape,
        color = SnackbarDefaults.color,
        contentColor = SnackbarDefaults.contentColor,
        shadowElevation = 6.dp,
    ) {
        Column {
            Row(
                Modifier.padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.removed_exercise, name),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = onUndo) {
                    Text(
                        stringResource(R.string.undo),
                        color = SnackbarDefaults.actionColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = SnackbarDefaults.actionColor,
                trackColor = SnackbarDefaults.color,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun AddExerciseDialog(
    choices: List<ProgrammedExercise>,
    female: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var area by remember { mutableStateOf<String?>(null) }
    val grouped = remember(choices) {
        choices.groupBy { it.primaryMuscles.firstOrNull()?.lowercase().orEmpty() }
    }
    val areas = WorkoutSize.bodyAreas.filter { grouped[it].orEmpty().isNotEmpty() } +
        grouped.keys.filter { it.isNotEmpty() && it !in WorkoutSize.bodyAreas }.sorted()
    Dialog(onDismissRequest = { if (area != null) area = null else onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val selected = area
                if (selected == null) {
                    Text(stringResource(R.string.choose_area), style = MaterialTheme.typography.headlineSmall)
                    if (areas.isEmpty()) {
                        Text(
                            stringResource(R.string.nothing_else_to_add),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    areas.forEach { muscle ->
                        val label = muscleLabel(muscle)
                        val count = grouped[muscle].orEmpty().size
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { area = muscle }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BodyDiagram(
                                muscles = listOf(muscle),
                                female = female,
                                modifier = Modifier.size(width = 56.dp, height = 64.dp),
                                contentDescription = label,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    pluralStringResource(R.plurals.area_exercises, count, count),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { area = null }) {
                        Text(stringResource(R.string.all_areas))
                    }
                    Text(muscleLabel(selected), style = MaterialTheme.typography.headlineSmall)
                    grouped[selected].orEmpty().sortedBy { it.name }.forEach { exercise ->
                        val muscles = exercise.primaryMuscles.joinToString(", ")
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onPick(exercise.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                exercise.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            ExerciseThumb(exercise.imageFiles.firstOrNull(), Modifier.size(48.dp))
                            BodyDiagram(
                                muscles = exercise.primaryMuscles,
                                female = female,
                                modifier = Modifier.size(width = 56.dp, height = 64.dp),
                                contentDescription = if (muscles.isBlank()) {
                                    null
                                } else {
                                    stringResource(R.string.works_muscles, muscles)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val muscleColors = mapOf(
    "chest" to Color(0xFFE85D4C),
    "shoulders" to Color(0xFFF0A202),
    "middle back" to Color(0xFF7EB6FF),
    "biceps" to Color(0xFFC77DFF),
    "triceps" to Color(0xFFFF8AD4),
    "abdominals" to Color(0xFFD6F25C),
    "lower back" to Color(0xFF5B8CFF),
    "glutes" to Color(0xFFFF6B8A),
    "quadriceps" to Color(0xFF3DDC97),
    "hamstrings" to Color(0xFF4ECDC4),
    "calves" to Color(0xFFF2C14E),
)

private fun muscleColor(muscle: String): Color = muscleColors[muscle.lowercase()] ?: Color(0xFFB0B8A4)

@Composable
private fun ProgressCard(lines: List<MuscleLine>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.titleMedium)
            if (lines.isEmpty()) {
                Text(
                    stringResource(R.string.progress_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.progress_caption),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                ProgressChart(lines)
                val dates = lines.flatMap { it.points }.map { it.date }.distinct().sorted()
                if (dates.isNotEmpty()) {
                    val format = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            dates.first().format(format),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (dates.size > 1) {
                            Text(
                                dates.last().format(format),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    lines.forEach { line ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(muscleColor(line.muscle)),
                            )
                            Text(muscleLabel(line.muscle), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressChart(lines: List<MuscleLine>) {
    val dates = lines.flatMap { it.points }.map { it.date }.distinct().sorted()
    Canvas(Modifier.fillMaxWidth().height(168.dp)) {
        if (dates.isNotEmpty()) {
            val padX = 8.dp.toPx()
            val padY = 14.dp.toPx()
            val maxLevel = lines.maxOf { line -> line.points.maxOf { it.level } }.coerceAtLeast(1.0)
            val top = maxLevel * 1.15
            val start = dates.first().toEpochDay()
            val span = (dates.last().toEpochDay() - start).coerceAtLeast(1)
            fun xOf(date: LocalDate): Float {
                if (dates.size == 1) return size.width / 2f
                val along = (date.toEpochDay() - start).toFloat() / span.toFloat()
                return padX + (size.width - padX * 2) * along
            }
            fun yOf(level: Double): Float {
                val plot = size.height - padY * 2
                return size.height - padY - (level / top).toFloat() * plot
            }
            if (maxLevel > 1.05) {
                val base = yOf(1.0)
                drawLine(
                    Color.White.copy(alpha = 0.18f),
                    Offset(padX, base),
                    Offset(size.width - padX, base),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            lines.forEach { line ->
                val color = muscleColor(line.muscle)
                val points = line.points.map { Offset(xOf(it.date), yOf(it.level)) }
                for (index in 0 until points.lastIndex) {
                    drawLine(
                        color,
                        points[index],
                        points[index + 1],
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                points.forEach { center ->
                    drawCircle(color, radius = 4.5.dp.toPx(), center = center)
                }
            }
        }
    }
}

@Composable
private fun muscleLabel(muscle: String): String {
    val res = when (muscle) {
        "chest" -> R.string.muscle_chest
        "shoulders" -> R.string.muscle_shoulders
        "middle back" -> R.string.muscle_middle_back
        "biceps" -> R.string.muscle_biceps
        "triceps" -> R.string.muscle_triceps
        "abdominals" -> R.string.muscle_abdominals
        "lower back" -> R.string.muscle_lower_back
        "glutes" -> R.string.muscle_glutes
        "quadriceps" -> R.string.muscle_quadriceps
        "hamstrings" -> R.string.muscle_hamstrings
        "calves" -> R.string.muscle_calves
        else -> null
    }
    return if (res == null) {
        muscle.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    } else {
        stringResource(res)
    }
}

@Composable
private fun exerciseDetail(planned: PlannedExercise): String {
    val reps = stringResource(R.string.prescription, planned.sets, prescription(planned))
    val load = planned.loadKg
    return if (load != null) "$reps · ${trimKg(load)} kg" else reps
}

private fun Modifier.tapControl(enabled: Boolean, label: String, onTap: () -> Unit): Modifier {
    return this
        .semantics {
            contentDescription = label
            role = Role.Button
        }
        .pointerInput(enabled, label) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pointerId = down.id
                down.consume()
                var moved = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
                    if (change.changedToUpIgnoreConsumed()) {
                        change.consume()
                        if (!moved && enabled) onTap()
                        return@awaitEachGesture
                    }
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        moved = true
                    }
                }
            }
        }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.arrangeExercises(
    exerciseAt: (Offset) -> Int?,
    onOpen: (Int) -> Unit,
    onDragStart: (Int, Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val slot = exerciseAt(down.position) ?: return@awaitEachGesture
        val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "cancel"
                if (change.changedToUpIgnoreConsumed()) return@withTimeoutOrNull "up"
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@withTimeoutOrNull "move"
                }
                if (change.isConsumed) return@withTimeoutOrNull "cancel"
            }
        }
        when (outcome) {
            "up" -> onOpen(slot)
            null -> {
                onDragStart(slot, down.position)
                val completed = try {
                    drag(down.id) { change ->
                        onMove(change.position)
                        change.consume()
                    }
                } catch (cancel: CancellationException) {
                    onCancel()
                    throw cancel
                }
                if (completed) onDrop() else onCancel()
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    date: LocalDate,
    isToday: Boolean,
    trained: Boolean,
    onOpen: (() -> Unit)?,
) {
    Box(
        Modifier
            .weight(1f)
            .height(40.dp)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(3.dp)
            .then(
                if (isToday) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            .background(
                if (trained) MaterialTheme.colorScheme.primary else Color.Transparent,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = if (trained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun WeekBars(weekInBlock: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { week ->
            val color = when {
                week == weekInBlock -> MaterialTheme.colorScheme.primary
                week < weekInBlock -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color),
            )
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
    if (model.today?.completed == true) {
        FinishedSession(model, plan)
        return
    }
    val steps = model.playSteps()
    val step = steps.getOrNull(model.stepIndex)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            TextButton(onClick = model::back) { Text(stringResource(R.string.back)) }
            if (model.askRating) {
                RatingBlock(model, Modifier.weight(1f))
            } else {
                when (step) {
                    is PlayStep.Rest -> RestStep(model, steps, Modifier.weight(1f))
                    is PlayStep.Work -> WorkStep(model, plan, step, Modifier.weight(1f))
                    null -> FinishedStep(model, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlayModeToggle(model: WorkoutViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeChip(
            label = stringResource(R.string.mode_each),
            selected = model.playMode == PlayMode.STRAIGHT,
            onClick = { model.choosePlayMode(PlayMode.STRAIGHT) },
        )
        ModeChip(
            label = stringResource(R.string.mode_row),
            selected = model.playMode == PlayMode.CIRCUIT,
            onClick = { model.choosePlayMode(PlayMode.CIRCUIT) },
        )
    }
}

@Composable
private fun RowScope.ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.weight(1f).height(48.dp)
    val shape = RoundedCornerShape(14.dp)
    if (selected) {
        Button(onClick = onClick, modifier = modifier, shape = shape) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = shape) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WorkStep(
    model: WorkoutViewModel,
    plan: workout.domain.SessionPlan,
    work: PlayStep.Work,
    modifier: Modifier,
) {
    val planned = plan.exercises.getOrNull(work.exerciseIndex)
    val exercise = planned?.let { model.catalog.find(it.exerciseId) }
    val draft = model.sets.getOrNull(work.exerciseIndex)?.getOrNull(work.setIndex)
    Column(modifier) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (planned != null) {
                Text(
                    exercise?.name ?: planned.exerciseId,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (model.playMode == PlayMode.CIRCUIT) {
                            val rounds = plan.exercises.maxOf { it.sets }.coerceAtLeast(1)
                            stringResource(
                                R.string.play_circuit_place,
                                work.setIndex + 1,
                                rounds,
                                work.exerciseIndex + 1,
                                plan.exercises.size,
                            )
                        } else {
                            stringResource(
                                R.string.play_straight_place,
                                work.setIndex + 1,
                                planned.sets,
                                work.exerciseIndex + 1,
                                plan.exercises.size,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (planned.anchor) {
                        Text(stringResource(R.string.main_lift_badge), color = MaterialTheme.colorScheme.primary)
                    }
                }
                LinearProgressIndicator(
                    progress = { workoutFraction(model, work) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                ExercisePhotos(
                    exercise?.imageFiles.orEmpty(),
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 148.dp),
                    photoHeight = 148.dp,
                )
                Text(
                    stringResource(R.string.prescription, planned.sets, prescription(planned)) +
                        loadSuffix(planned.loadKg, exercise?.loadType),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (planned.reason.isNotBlank()) {
                    Text(
                        planned.reason,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (draft != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.reps_label),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { model.changeReps(-1) }) { Text("−") }
                    Text(draft.reps.toString(), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { model.changeReps(1) }) { Text("+") }
                }
            }
            if (exercise?.loadType == LoadType.DUMBBELL) {
                DumbbellPicker(
                    owned = model.profile?.dumbbellKg.orEmpty(),
                    selected = draft?.loadKg,
                    onSelect = model::selectLoad,
                )
            }
            if (model.coachMessage.isNotBlank()) {
                Text(model.coachMessage, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row {
                TextButton(onClick = model::openHowTo) { Text(stringResource(R.string.how_to)) }
                TextButton(onClick = model::requestFinish) { Text(stringResource(R.string.finish)) }
            }
        }
        MainAction(onClick = model::completeCurrent, label = stringResource(R.string.done))
        SkipAction(model)
    }
}

@Composable
private fun RestStep(model: WorkoutViewModel, steps: List<PlayStep>, modifier: Modifier) {
    val next = steps.drop(model.stepIndex + 1).firstNotNullOfOrNull { it as? PlayStep.Work }
    val nextName = next?.let { work ->
        val planned = model.today?.plan?.exercises?.getOrNull(work.exerciseIndex)
        planned?.let { model.catalog.find(it.exerciseId)?.name ?: it.exerciseId }
    }
    Column(modifier) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.rest),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                model.restRemaining.toString(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 96.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            if (next != null && nextName != null) {
                Text(
                    stringResource(R.string.next_set, nextName, next.setIndex + 1),
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        SkipAction(model)
    }
}

@Composable
private fun FinishedStep(model: WorkoutViewModel, modifier: Modifier) {
    Column(modifier) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.workout_complete),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        MainAction(onClick = model::requestFinish, label = stringResource(R.string.finish))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RatingBlock(model: WorkoutViewModel, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.rate_prompt),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
        )
        SessionRating.entries.forEach { rating ->
            Button(
                onClick = { model.rate(rating) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(ratingLabel(rating), style = MaterialTheme.typography.titleMedium)
            }
        }
        TextButton(onClick = model::dismissRating, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun MainAction(onClick: () -> Unit, label: String) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(84.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SkipAction(model: WorkoutViewModel) {
    TextButton(onClick = model::skipStep, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(stringResource(R.string.skip), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FinishedSession(model: WorkoutViewModel, plan: workout.domain.SessionPlan) {
    ScreenFrame(title = stringResource(R.string.review_workout), onBack = model::back) {
        plan.exercises.forEachIndexed { index, planned ->
            val exercise = model.catalog.find(planned.exerciseId)
            Text(exercise?.name ?: planned.exerciseId, style = MaterialTheme.typography.titleMedium)
            model.sets.getOrNull(index).orEmpty().forEachIndexed { setIndex, draft ->
                Text(
                    stringResource(R.string.review_set, setIndex + 1, draft.reps) +
                        loadSuffix(draft.loadKg, exercise?.loadType),
                )
            }
        }
    }
}

@Composable
private fun DumbbellPicker(owned: List<Double>, selected: Double?, onSelect: (Double) -> Unit) {
    val selectedIndex = owned.indexOfFirst { selected != null && abs(selected - it) < 0.05 }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0),
    )
    LaunchedEffect(selectedIndex, owned) {
        if (selectedIndex >= 0) {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.find { it.index == selectedIndex }
            val fullyVisible = item != null &&
                item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset
            if (!fullyVisible) listState.scrollToItem(selectedIndex)
        }
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        items(count = owned.size, key = { it }) { index ->
            val kilograms = owned[index]
            val picked = index == selectedIndex
            val label = "${trimKg(kilograms)} kg"
            if (picked) {
                Button(onClick = { onSelect(kilograms) }) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(kilograms) }) { Text(label) }
            }
        }
    }
}

private fun workoutFraction(model: WorkoutViewModel, work: PlayStep.Work): Float {
    val works = model.playSteps().filterIsInstance<PlayStep.Work>()
    val place = works.indexOfFirst { it.exerciseIndex == work.exerciseIndex && it.setIndex == work.setIndex }
    if (works.isEmpty() || place < 0) return 0f
    return (place + 1f) / works.size
}

@Composable
private fun GuidanceScreen(onAccept: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Text(
                stringResource(R.string.guidance_accept),
                modifier = Modifier.clickable { accepted = !accepted },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAccept,
            enabled = accepted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.guidance_continue), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PastSessionScreen(model: WorkoutViewModel, date: LocalDate) {
    val entry = model.history.find { it.date == date }
    val title = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))
    ScreenFrame(title = title, onBack = model::back) {
        if (entry == null) return@ScreenFrame
        entry.rating?.let {
            Text(ratingLabel(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        }
        if (entry.note.isNotBlank()) Text(entry.note)
        entry.lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyLarge)
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
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = model::back) { Text(stringResource(R.string.back)) }
            Text(exercise?.name ?: id, style = MaterialTheme.typography.headlineMedium)
            if (exercise != null) {
                HardnessChooser(model, exercise)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ExercisePhotos(exercise.imageFiles)
                    if (exercise.primaryMuscles.isNotEmpty()) {
                        Text(exercise.primaryMuscles.joinToString(", "))
                    }
                    Text(stringResource(R.string.how_to_title), style = MaterialTheme.typography.titleMedium)
                    exercise.instructions.forEachIndexed { index, step ->
                        Text("${index + 1}. $step")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun HardnessChooser(model: WorkoutViewModel, exercise: ProgrammedExercise) {
    val session = model.today ?: return
    val todayId = session.plan.exercises.firstOrNull { planned ->
        model.catalog.find(planned.exerciseId)?.familyId == exercise.familyId
    }?.exerciseId ?: return
    val todayExercise = model.catalog.find(todayId) ?: return
    val eligible = model.profile?.let { profile -> model.catalog.eligible(profile).map { it.id }.toSet() }
    fun allowed(id: String) = id == todayExercise.id || eligible == null || id in eligible
    val versions = model.catalog.inFamily(exercise.familyId).filter { allowed(it.id) }
    if (versions.size < 2) return
    val index = versions.indexOfFirst { it.id == exercise.id }.let { found ->
        if (found >= 0) found else versions.indexOfFirst { it.id == todayExercise.id }
    }.coerceAtLeast(0)
    val taken = session.plan.exercises.map { it.exerciseId }.toSet()
    val easier = versions.getOrNull(index - 1)?.takeUnless { it.id in taken }
    val harder = versions.getOrNull(index + 1)?.takeUnless { it.id in taken }
    VersionFrame(
        name = exercise.name,
        easierEnabled = easier != null,
        harderEnabled = harder != null,
        onEasier = { if (easier != null) model.useThisToday(easier.id) },
        onHarder = { if (harder != null) model.useThisToday(harder.id) },
    )
}

@Composable
private fun VersionFrame(
    name: String,
    easierEnabled: Boolean,
    harderEnabled: Boolean,
    onEasier: () -> Unit,
    onHarder: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(96.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepButton(
            label = stringResource(R.string.easier_side),
            enabled = easierEnabled,
            onClick = onEasier,
        )
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(
                    name,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        StepButton(
            label = stringResource(R.string.harder_side),
            enabled = harderEnabled,
            onClick = onHarder,
        )
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(84.dp)
            .fillMaxHeight()
            .alpha(if (enabled) 1f else 0.35f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun sessionMinutes(count: Int): Int = when {
    count <= 3 -> 15
    count == 4 -> 20
    count == 5 -> 30
    count == 6 -> 45
    else -> 60
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
        Text(stringResource(R.string.workout_order), style = MaterialTheme.typography.titleMedium)
        PlayModeToggle(model)
        Text(
            stringResource(
                if (model.playMode == PlayMode.CIRCUIT) R.string.mode_row_note else R.string.mode_each_note,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
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
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
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
private fun ExercisePhotos(
    files: List<String>,
    modifier: Modifier = Modifier,
    photoHeight: Dp = 240.dp,
) {
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
        modifier
            .fillMaxWidth()
            .height(photoHeight)
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
