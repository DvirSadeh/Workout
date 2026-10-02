package workout.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface DriveFetch {
    data object Restored : DriveFetch
    data object Empty : DriveFetch
    data class Failed(val message: String) : DriveFetch
}

data class BackupSnapshot(
    val profile: ProfileEntity?,
    val sessions: List<SessionEntity>,
    val sets: List<SetEntity>,
    val adjustments: List<AdjustmentEntity>,
    val apiKey: String?,
    val accountEmail: String?,
)

object BackupCodec {
    fun export(
        profile: ProfileEntity?,
        sessions: List<SessionEntity>,
        sets: List<SetEntity>,
        adjustments: List<AdjustmentEntity>,
        apiKey: String?,
        accountEmail: String?,
    ): String {
        val sessionRows = JSONArray()
        sessions.forEach { session ->
            sessionRows.put(
                JSONObject()
                    .put("date", session.date)
                    .put("rating", session.rating ?: JSONObject.NULL)
                    .put("source", session.source)
                    .put("note", session.note)
                    .put("focus", session.focus)
                    .put("planJson", session.planJson)
                    .put("completed", session.completed),
            )
        }
        val setRows = JSONArray()
        sets.forEach { set ->
            setRows.put(
                JSONObject()
                    .put("date", set.date)
                    .put("exerciseId", set.exerciseId)
                    .put("setIndex", set.setIndex)
                    .put("reps", set.reps)
                    .put("loadKg", set.loadKg ?: JSONObject.NULL)
                    .put("completed", set.completed),
            )
        }
        val adjustmentRows = JSONArray()
        adjustments.forEach { row ->
            adjustmentRows.put(
                JSONObject()
                    .put("date", row.date)
                    .put("exerciseId", row.exerciseId)
                    .put("familyId", row.familyId)
                    .put("direction", row.direction),
            )
        }
        val root = JSONObject()
            .put("version", 1)
            .put("profile", profile?.toJson() ?: JSONObject.NULL)
            .put("sessions", sessionRows)
            .put("sets", setRows)
            .put("adjustments", adjustmentRows)
        if (apiKey != null) root.put("apiKey", apiKey)
        if (accountEmail != null) root.put("accountEmail", accountEmail)
        return root.toString()
    }

    fun parse(json: String): BackupSnapshot {
        val root = JSONObject(json)
        val version = root.optInt("version", 0)
        if (version != 1) throw IllegalArgumentException("This backup is from a newer Workout app.")
        val sessions = root.getJSONArray("sessions")
        val sets = root.getJSONArray("sets")
        val adjustments = root.getJSONArray("adjustments")
        return BackupSnapshot(
            profile = if (root.isNull("profile")) null else profileFrom(root.getJSONObject("profile")),
            sessions = List(sessions.length()) { index -> sessionFrom(sessions.getJSONObject(index)) },
            sets = List(sets.length()) { index -> setFrom(sets.getJSONObject(index)) },
            adjustments = List(adjustments.length()) { index -> adjustmentFrom(adjustments.getJSONObject(index)) },
            apiKey = if (root.has("apiKey") && !root.isNull("apiKey")) root.getString("apiKey") else null,
            accountEmail = if (root.has("accountEmail")) root.optString("accountEmail") else null,
        )
    }
}

class DriveClient {
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()

    fun upload(token: String, json: String) {
        val existing = findId(token)
        val request = if (existing == null) {
            val boundary = "workout${System.currentTimeMillis()}"
            val body = buildString {
                append("--$boundary\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append("{\"name\":\"$FILE_NAME\",\"parents\":[\"appDataFolder\"]}\r\n")
                append("--$boundary\r\n")
                append("Content-Type: application/json\r\n\r\n")
                append(json)
                append("\r\n--$boundary--")
            }
            Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $token")
                .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .build()
        } else {
            Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$existing?uploadType=media")
                .header("Authorization", "Bearer $token")
                .patch(json.toRequestBody("application/json".toMediaType()))
                .build()
        }
        execute(request)
    }

    fun download(token: String): String? {
        val id = findId(token) ?: return null
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$id?alt=media")
            .header("Authorization", "Bearer $token")
            .build()
        return execute(request)
    }

    private fun findId(token: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name)&q=${encode(QUERY)}"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        val body = execute(request)
        val files = JSONObject(body).optJSONArray("files") ?: return null
        for (index in 0 until files.length()) {
            val file = files.getJSONObject(index)
            if (file.optString("name") == FILE_NAME) return file.getString("id")
        }
        return null
    }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                throw IllegalStateException("Google Drive refused the backup (${response.code}).")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Drive backup failed (${response.code}).")
            }
            return body
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val FILE_NAME = "workout-backup.json"
        const val QUERY = "name = 'workout-backup.json'"
    }
}

private fun ProfileEntity.toJson(): JSONObject = JSONObject()
    .put("birthYear", birthYear)
    .put("sex", sex)
    .put("heightCm", heightCm)
    .put("weightKg", weightKg)
    .put("experience", experience)
    .put("goal", goal)
    .put("daysPerWeek", daysPerWeek)
    .put("minutesPerSession", minutesPerSession)
    .put("dumbbells", dumbbells)
    .put("hasBench", hasBench)
    .put("limits", limits)
    .put("trainingStart", trainingStart)

private fun profileFrom(json: JSONObject) = ProfileEntity(
    birthYear = json.getInt("birthYear"),
    sex = json.getString("sex"),
    heightCm = json.getInt("heightCm"),
    weightKg = json.getDouble("weightKg"),
    experience = json.getString("experience"),
    goal = json.getString("goal"),
    daysPerWeek = json.getInt("daysPerWeek"),
    minutesPerSession = json.getInt("minutesPerSession"),
    dumbbells = json.getString("dumbbells"),
    hasBench = json.getBoolean("hasBench"),
    limits = json.optString("limits"),
    trainingStart = json.getString("trainingStart"),
)

private fun sessionFrom(json: JSONObject) = SessionEntity(
    date = json.getString("date"),
    rating = if (json.isNull("rating")) null else json.getString("rating"),
    source = json.getString("source"),
    note = json.getString("note"),
    focus = json.getString("focus"),
    planJson = json.getString("planJson"),
    completed = json.getBoolean("completed"),
)

private fun setFrom(json: JSONObject) = SetEntity(
    date = json.getString("date"),
    exerciseId = json.getString("exerciseId"),
    setIndex = json.getInt("setIndex"),
    reps = json.getInt("reps"),
    loadKg = if (json.isNull("loadKg")) null else json.getDouble("loadKg"),
    completed = json.getBoolean("completed"),
)

private fun adjustmentFrom(json: JSONObject) = AdjustmentEntity(
    date = json.getString("date"),
    exerciseId = json.getString("exerciseId"),
    familyId = json.getString("familyId"),
    direction = json.getString("direction"),
)
