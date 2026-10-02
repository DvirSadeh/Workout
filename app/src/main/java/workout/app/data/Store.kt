package workout.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import workout.domain.AdjustmentDirection
import workout.domain.AdjustmentRecord
import workout.domain.Catalog
import workout.domain.CuratedCatalog
import workout.domain.DayFocus
import workout.domain.ExerciseText
import workout.domain.Experience
import workout.domain.Goal
import workout.domain.LimitTag
import workout.domain.LoggedExercise
import workout.domain.LoggedSet
import workout.domain.PlanSource
import workout.domain.PlannedExercise
import workout.domain.SessionPlan
import workout.domain.SessionRating
import workout.domain.SessionRecord
import workout.domain.Sex
import workout.domain.UserProfile
import java.time.LocalDate

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val birthYear: Int,
    val sex: String,
    val heightCm: Int,
    val weightKg: Double,
    val experience: String,
    val goal: String,
    val daysPerWeek: Int,
    val minutesPerSession: Int,
    val dumbbells: String,
    val hasBench: Boolean,
    val limits: String,
    val trainingStart: String,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val date: String,
    val rating: String?,
    val source: String,
    val note: String,
    val focus: String,
    val planJson: String,
    val completed: Boolean,
)

@Entity(primaryKeys = ["date", "exerciseId", "setIndex"], tableName = "sets")
data class SetEntity(
    val date: String,
    val exerciseId: String,
    val setIndex: Int,
    val reps: Int,
    val loadKg: Double?,
    val completed: Boolean,
)

@Entity(tableName = "adjustments")
data class AdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val exerciseId: String,
    val familyId: String,
    val direction: String,
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun profile(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: ProfileEntity)

    @Query("SELECT * FROM sessions WHERE date = :date")
    suspend fun session(date: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE date = :date AND completed = 0")
    suspend fun deleteOpenSession(date: String)

    @Query("SELECT * FROM sessions ORDER BY date")
    suspend fun sessions(): List<SessionEntity>

    @Query("SELECT * FROM sets WHERE date = :date ORDER BY exerciseId, setIndex")
    suspend fun sets(date: String): List<SetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSets(sets: List<SetEntity>)

    @Query("DELETE FROM sets WHERE date = :date")
    suspend fun deleteSets(date: String)

    @Query("DELETE FROM adjustments WHERE date = :date")
    suspend fun deleteAdjustments(date: String)

    @Insert
    suspend fun saveAdjustments(rows: List<AdjustmentEntity>)

    @Query("SELECT * FROM adjustments WHERE date = :date")
    suspend fun adjustments(date: String): List<AdjustmentEntity>

    @Query("DELETE FROM profile")
    suspend fun deleteProfiles()

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM sets")
    suspend fun deleteAllSets()

    @Query("DELETE FROM adjustments")
    suspend fun deleteAllAdjustments()

    @Query("SELECT * FROM sets")
    suspend fun allSets(): List<SetEntity>

    @Query("SELECT * FROM adjustments")
    suspend fun allAdjustments(): List<AdjustmentEntity>

    @Transaction
    suspend fun replaceBackup(
        profile: ProfileEntity?,
        sessions: List<SessionEntity>,
        sets: List<SetEntity>,
        adjustments: List<AdjustmentEntity>,
    ) {
        deleteProfiles()
        deleteAllSessions()
        deleteAllSets()
        deleteAllAdjustments()
        if (profile != null) saveProfile(profile)
        sessions.forEach { saveSession(it) }
        if (sets.isNotEmpty()) saveSets(sets)
        if (adjustments.isNotEmpty()) saveAdjustments(adjustments)
    }
}

@Database(
    entities = [ProfileEntity::class, SessionEntity::class, SetEntity::class, AdjustmentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao

    companion object {
        private val open = mutableMapOf<String, WorkoutDatabase>()

        fun create(context: Context, name: String): WorkoutDatabase = synchronized(this) {
            open.getOrPut(name) {
                Room.databaseBuilder(
                    context.applicationContext,
                    WorkoutDatabase::class.java,
                    name,
                ).build()
            }
        }
    }
}

class KeyStore(context: Context) {
    private val prefs = runCatching {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "coach_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences("coach_keys_plain", Context.MODE_PRIVATE)
    }

    fun read(accountId: String): String = prefs.getString(accountKey(accountId), "").orEmpty()

    fun write(accountId: String, value: String) {
        prefs.edit().putString(accountKey(accountId), value.trim()).apply()
    }

    fun readLegacy(): String = prefs.getString(LEGACY, "").orEmpty()

    private fun accountKey(accountId: String) = "xai_api_key_$accountId"

    private companion object {
        const val LEGACY = "xai_api_key"
    }
}

fun loadCatalog(context: Context): Catalog {
    val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
    val array = JSONArray(text)
    val details = buildMap {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val instructions = item.getJSONArray("instructions")
            val muscles = item.getJSONArray("primaryMuscles")
            val images = item.getJSONArray("images")
            put(
                item.getString("id"),
                ExerciseText(
                    name = item.getString("name"),
                    instructions = jsonStrings(instructions),
                    primaryMuscles = jsonStrings(muscles),
                    imageFiles = jsonStrings(images),
                ),
            )
        }
    }
    return CuratedCatalog.catalog(details)
}

fun ProfileEntity.toProfile(): UserProfile = UserProfile(
    birthYear = birthYear,
    sex = Sex.valueOf(sex),
    heightCm = heightCm,
    weightKg = weightKg,
    experience = Experience.valueOf(experience),
    goal = Goal.valueOf(goal),
    daysPerWeek = daysPerWeek,
    minutesPerSession = minutesPerSession,
    dumbbellKg = dumbbells.split(",").mapNotNull { it.trim().toDoubleOrNull() },
    hasBench = hasBench,
    limits = limits.split(",").mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() }?.let { LimitTag.valueOf(it) } }.toSet(),
    trainingStart = LocalDate.parse(trainingStart),
)

fun UserProfile.toEntity(): ProfileEntity = ProfileEntity(
    birthYear = birthYear,
    sex = sex.name,
    heightCm = heightCm,
    weightKg = weightKg,
    experience = experience.name,
    goal = goal.name,
    daysPerWeek = daysPerWeek,
    minutesPerSession = minutesPerSession,
    dumbbells = dumbbellKg.joinToString(","),
    hasBench = hasBench,
    limits = limits.joinToString(",") { it.name },
    trainingStart = trainingStart.toString(),
)

fun SessionPlan.toJson(): String {
    val exercises = JSONArray()
    this.exercises.forEach { planned ->
        exercises.put(
            JSONObject()
                .put("exerciseId", planned.exerciseId)
                .put("sets", planned.sets)
                .put("repsLow", planned.repsLow)
                .put("repsHigh", planned.repsHigh)
                .put("loadKg", planned.loadKg ?: JSONObject.NULL)
                .put("restSeconds", planned.restSeconds)
                .put("reason", planned.reason)
                .put("anchor", planned.anchor),
        )
    }
    return JSONObject()
        .put("date", date.toString())
        .put("note", note)
        .put("source", source.name)
        .put("blockIndex", blockIndex)
        .put("weekInBlock", weekInBlock)
        .put("focus", focus.name)
        .put("deload", deload)
        .put("exercises", exercises)
        .toString()
}

fun parsePlan(json: String): SessionPlan {
    val obj = JSONObject(json)
    val exercises = obj.getJSONArray("exercises")
    return SessionPlan(
        date = LocalDate.parse(obj.getString("date")),
        note = obj.getString("note"),
        source = PlanSource.valueOf(obj.getString("source")),
        blockIndex = obj.getInt("blockIndex"),
        weekInBlock = obj.getInt("weekInBlock"),
        focus = DayFocus.valueOf(obj.getString("focus")),
        deload = obj.getBoolean("deload"),
        exercises = List(exercises.length()) { index ->
            val item = exercises.getJSONObject(index)
            PlannedExercise(
                exerciseId = item.getString("exerciseId"),
                sets = item.getInt("sets"),
                repsLow = item.getInt("repsLow"),
                repsHigh = item.getInt("repsHigh"),
                loadKg = if (item.isNull("loadKg")) null else item.getDouble("loadKg"),
                restSeconds = item.getInt("restSeconds"),
                reason = item.getString("reason"),
                anchor = item.getBoolean("anchor"),
            )
        },
    )
}

fun sessionRecord(
    entity: SessionEntity,
    sets: List<SetEntity>,
    adjustments: List<AdjustmentEntity>,
): SessionRecord {
    val plan = parsePlan(entity.planJson)
    val byExercise = sets.groupBy { it.exerciseId }
    return SessionRecord(
        date = plan.date,
        rating = entity.rating?.let { SessionRating.valueOf(it) },
        source = plan.source,
        note = plan.note,
        focus = plan.focus,
        exercises = plan.exercises.map { planned ->
            val logged = byExercise[planned.exerciseId].orEmpty().sortedBy { it.setIndex }
            LoggedExercise(
                exerciseId = planned.exerciseId,
                prescribedSets = planned.sets,
                repsLow = planned.repsLow,
                repsHigh = planned.repsHigh,
                prescribedLoadKg = planned.loadKg,
                sets = logged.map { LoggedSet(it.reps, it.loadKg, it.completed) },
                anchor = planned.anchor,
            )
        },
        adjustments = adjustments.map {
            AdjustmentRecord(it.exerciseId, it.familyId, AdjustmentDirection.valueOf(it.direction))
        },
    )
}

private fun jsonStrings(array: JSONArray): List<String> = List(array.length()) { array.getString(it) }
