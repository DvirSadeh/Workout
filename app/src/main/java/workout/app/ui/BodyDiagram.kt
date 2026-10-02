package workout.app.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.min
import org.json.JSONObject

private val posteriorMuscles = setOf("middle back", "lower back", "glutes", "hamstrings", "triceps")
private val anteriorMuscles = setOf("chest", "biceps", "abdominals", "quadriceps")

private val groupsFor = mapOf(
    "chest" to setOf("CHEST"),
    "shoulders" to setOf("SHOULDERS_FRONT", "SHOULDERS_SIDE", "SHOULDERS_REAR"),
    "middle back" to setOf("LATS", "RHOMBOIDS", "TRAPEZIUS"),
    "biceps" to setOf("BICEPS"),
    "triceps" to setOf("TRICEPS"),
    "abdominals" to setOf("CORE", "OBLIQUES"),
    "lower back" to setOf("BACK_LOWER"),
    "glutes" to setOf("GLUTES"),
    "quadriceps" to setOf("QUADS"),
    "hamstrings" to setOf("HAMSTRINGS"),
    "calves" to setOf("CALVES"),
)

private val bodyTop = Color(0xFF2A3524)
private val bodyBottom = Color(0xFF141A12)
private val idleTop = Color(0xFF5C6B50)
private val idleBottom = Color(0xFF394232)
private val seam = Color(0xFF10150E)

@Composable
fun BodyDiagram(
    muscles: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    female: Boolean = false,
) {
    val names = muscles.map { it.lowercase() }.toSet()
    val back = names.any { it in posteriorMuscles } && names.none { it in anteriorMuscles }
    val activeGroups = names.flatMap { groupsFor[it].orEmpty() }.toSet()
    val context = LocalContext.current
    val figure = remember(female, back) { MuscleFigures.load(context, female, back) }
    val active = MaterialTheme.colorScheme.primary
    Canvas(
        modifier.then(
            if (contentDescription.isNullOrBlank()) {
                Modifier
            } else {
                Modifier.semantics { this.contentDescription = contentDescription }
            },
        ),
    ) {
        if (figure.muscles.isNotEmpty()) {
            val fitted = min(size.width / figure.width, size.height / figure.height)
            val dx = (size.width - figure.width * fitted) / 2f
            val dy = (size.height - figure.height * fitted) / 2f
            translate(dx, dy) {
                scale(fitted, fitted, pivot = Offset.Zero) {
                    val body = Brush.verticalGradient(listOf(bodyTop, bodyBottom), startY = 0f, endY = figure.height)
                    figure.outline.forEach { part -> drawPart(part, body, figure.centerX, separate = false) }
                    val dim = figure.muscles.filter { it.group !in activeGroups }
                    val lit = figure.muscles.filter { it.group in activeGroups }
                    dim.forEach { part -> drawShaded(part, idleTop, idleBottom, figure.centerX) }
                    lit.forEach { part -> drawShaded(part, lerp(active, Color.White, 0.18f), active, figure.centerX) }
                }
            }
        }
    }
}

private fun DrawScope.drawShaded(part: MusclePart, top: Color, bottom: Color, centerX: Float) {
    val bounds = part.path.getBounds()
    val brush = Brush.verticalGradient(
        0f to top,
        0.55f to lerp(top, bottom, 0.45f),
        1f to bottom,
        startY = bounds.top,
        endY = bounds.bottom.coerceAtLeast(bounds.top + 1f),
    )
    drawPart(part, brush, centerX, separate = true)
}

private fun DrawScope.drawPart(part: MusclePart, brush: Brush, centerX: Float, separate: Boolean) {
    drawOne(part.path, brush, separate)
    if (part.side == "LEFT") {
        withTransform({
            scale(scaleX = -1f, scaleY = 1f, pivot = Offset(centerX, 0f))
        }) {
            drawOne(part.path, brush, separate)
        }
    }
}

private fun DrawScope.drawOne(path: Path, brush: Brush, separate: Boolean) {
    drawPath(path, brush)
    if (separate) drawPath(path, seam, style = Stroke(width = 4.5f))
}

private object MuscleFigures {
    private val cache = HashMap<String, MuscleFigure>()

    fun load(context: Context, female: Boolean, back: Boolean): MuscleFigure {
        val sex = if (female) "female" else "male"
        val view = if (back) "back" else "front"
        val name = "musclemap/$sex-$view.json"
        return synchronized(cache) {
            cache.getOrPut(name) { parse(context, name) }
        }
    }

    private fun parse(context: Context, name: String): MuscleFigure {
        val text = context.assets.open(name).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val outline = json.getJSONArray("outline")
        val muscles = json.getJSONArray("muscles")
        return MuscleFigure(
            width = json.getDouble("w").toFloat(),
            height = json.getDouble("h").toFloat(),
            centerX = json.getDouble("cx").toFloat(),
            outline = List(outline.length()) { index -> part(outline.getJSONObject(index), "") },
            muscles = List(muscles.length()) { index ->
                val item = muscles.getJSONObject(index)
                part(item, item.getString("group"))
            },
        )
    }

    private fun part(item: JSONObject, group: String): MusclePart {
        val parsed = runCatching { PathParser().parsePathString(item.getString("d")).toPath() }.getOrNull()
        return MusclePart(group, item.optString("side", "CENTER"), parsed ?: Path())
    }
}

private class MuscleFigure(
    val width: Float,
    val height: Float,
    val centerX: Float,
    val outline: List<MusclePart>,
    val muscles: List<MusclePart>,
)

private class MusclePart(val group: String, val side: String, val path: Path)
