package workout.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private val posteriorMuscles = setOf("middle back", "lower back", "glutes", "hamstrings", "triceps")
private val anteriorMuscles = setOf("chest", "biceps", "abdominals", "quadriceps")

@Composable
fun BodyDiagram(
    muscles: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val names = muscles.map { it.lowercase() }.toSet()
    val back = names.any { it in posteriorMuscles } && names.none { it in anteriorMuscles }
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val ink = MaterialTheme.colorScheme.background
    Canvas(
        modifier.then(
            if (contentDescription.isNullOrBlank()) {
                Modifier
            } else {
                Modifier.semantics { this.contentDescription = contentDescription }
            },
        ),
    ) {
        fun marks(muscle: String) = muscle in names
        limb(0.33f, 0.54f, 0.48f, 0.98f, idle)
        limb(0.52f, 0.54f, 0.67f, 0.98f, idle)
        limb(0.12f, 0.22f, 0.30f, 0.52f, idle)
        limb(0.70f, 0.22f, 0.88f, 0.52f, idle)
        limb(0.30f, 0.20f, 0.70f, 0.56f, idle)
        limb(0.44f, 0.15f, 0.56f, 0.22f, idle)
        drawCircle(idle, radius = size.height * 0.075f, center = Offset(size.width * 0.5f, size.height * 0.09f))
        if (back) {
            limb(0.47f, 0.24f, 0.53f, 0.50f, ink)
            if (marks("middle back")) limb(0.34f, 0.24f, 0.66f, 0.40f, active)
            if (marks("lower back")) limb(0.38f, 0.40f, 0.62f, 0.50f, active)
            if (marks("glutes")) limb(0.32f, 0.50f, 0.68f, 0.64f, active)
            if (marks("hamstrings")) {
                limb(0.34f, 0.62f, 0.47f, 0.80f, active)
                limb(0.53f, 0.62f, 0.66f, 0.80f, active)
            }
            if (marks("triceps")) {
                limb(0.14f, 0.24f, 0.28f, 0.40f, active)
                limb(0.72f, 0.24f, 0.86f, 0.40f, active)
            }
        } else {
            drawCircle(ink, radius = size.height * 0.012f, center = Offset(size.width * 0.44f, size.height * 0.085f))
            drawCircle(ink, radius = size.height * 0.012f, center = Offset(size.width * 0.56f, size.height * 0.085f))
            if (marks("chest")) {
                limb(0.36f, 0.28f, 0.48f, 0.40f, active)
                limb(0.52f, 0.28f, 0.64f, 0.40f, active)
            }
            if (marks("biceps")) {
                limb(0.14f, 0.24f, 0.28f, 0.40f, active)
                limb(0.72f, 0.24f, 0.86f, 0.40f, active)
            }
            if (marks("abdominals")) limb(0.40f, 0.40f, 0.60f, 0.54f, active)
            if (marks("quadriceps")) {
                limb(0.34f, 0.56f, 0.47f, 0.76f, active)
                limb(0.53f, 0.56f, 0.66f, 0.76f, active)
            }
        }
        if (marks("shoulders")) {
            drawCircle(active, radius = size.height * 0.045f, center = Offset(size.width * 0.28f, size.height * 0.24f))
            drawCircle(active, radius = size.height * 0.045f, center = Offset(size.width * 0.72f, size.height * 0.24f))
        }
        if (marks("calves")) {
            limb(0.35f, 0.78f, 0.47f, 0.96f, active)
            limb(0.53f, 0.78f, 0.65f, 0.96f, active)
        }
    }
}

private fun DrawScope.limb(left: Float, top: Float, right: Float, bottom: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(left * size.width, top * size.height),
        size = Size((right - left) * size.width, (bottom - top) * size.height),
        cornerRadius = CornerRadius(size.minDimension * 0.12f),
    )
}
