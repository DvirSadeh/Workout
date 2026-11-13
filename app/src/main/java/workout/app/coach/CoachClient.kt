package workout.app.coach

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import workout.domain.Catalog
import workout.domain.CoachRequest
import workout.domain.DayFocus
import workout.domain.LoadType
import workout.domain.PlanSource
import workout.domain.PlannedExercise
import workout.domain.SessionPlan
import workout.domain.Training
import workout.domain.UserProfile
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class CoachClient {
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()

    fun propose(
        apiKey: String,
        request: CoachRequest,
        profile: UserProfile,
        catalog: Catalog,
        today: LocalDate,
        violations: List<String> = emptyList(),
    ): SessionPlan? {
        val userText = if (violations.isEmpty()) {
            request.payloadJson
        } else {
            request.payloadJson + "\n\nThe previous plan was rejected:\n" + violations.joinToString("\n")
        }
        val body = JSONObject()
            .put("model", MODEL)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", request.systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userText)),
            )
            .put("response_format", responseFormat())
        val call = Request.Builder()
            .url("https://api.x.ai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val response = http.newCall(call).execute()
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) return null
        val content = JSONObject(raw)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        return parse(content, profile, catalog, today)
    }

    private fun parse(content: String, profile: UserProfile, catalog: Catalog, today: LocalDate): SessionPlan? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = JSONObject(content.substring(start, end + 1))
        val items = obj.getJSONArray("exercises")
        val dose = Training.dose(profile, today)
        val exercises = List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val id = item.getString("exerciseId")
            val programmed = catalog.find(id)
            val rawLoad = item.optDouble("loadKg", 0.0)
            val load = when {
                programmed?.loadType == LoadType.BODYWEIGHT -> null
                rawLoad <= 0.0 -> null
                else -> rawLoad
            }
            PlannedExercise(
                exerciseId = id,
                sets = item.getInt("sets"),
                repsLow = item.getInt("repsLow"),
                repsHigh = item.getInt("repsHigh"),
                loadKg = load,
                restSeconds = item.getInt("restSeconds"),
                reason = item.optString("reason"),
                anchor = item.optBoolean("anchor"),
            )
        }
        return SessionPlan(
            date = today,
            note = obj.optString("note"),
            exercises = exercises,
            source = PlanSource.COACH,
            blockIndex = Training.blockIndex(profile, today),
            weekInBlock = Training.weekInBlock(profile, today),
            focus = Training.focus(profile, emptyList(), today),
            deload = dose.deload,
        )
    }

    private fun responseFormat(): JSONObject {
        val exercise = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("exerciseId", JSONObject().put("type", "string"))
                    .put("sets", JSONObject().put("type", "integer"))
                    .put("repsLow", JSONObject().put("type", "integer"))
                    .put("repsHigh", JSONObject().put("type", "integer"))
                    .put("loadKg", JSONObject().put("type", "number"))
                    .put("restSeconds", JSONObject().put("type", "integer"))
                    .put("reason", JSONObject().put("type", "string"))
                    .put("anchor", JSONObject().put("type", "boolean")),
            )
            .put(
                "required",
                JSONArray(listOf("exerciseId", "sets", "repsLow", "repsHigh", "loadKg", "restSeconds", "reason", "anchor")),
            )
            .put("additionalProperties", false)
        val schema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("note", JSONObject().put("type", "string"))
                    .put("exercises", JSONObject().put("type", "array").put("items", exercise)),
            )
            .put("required", JSONArray(listOf("note", "exercises")))
            .put("additionalProperties", false)
        return JSONObject()
            .put("type", "json_schema")
            .put(
                "json_schema",
                JSONObject().put("name", "session_plan").put("strict", true).put("schema", schema),
            )
    }

    companion object {
        const val MODEL = "grok-4.7"
        private val JSON = "application/json".toMediaType()
    }
}

fun correctedFocus(plan: SessionPlan, focus: DayFocus): SessionPlan = plan.copy(focus = focus)
