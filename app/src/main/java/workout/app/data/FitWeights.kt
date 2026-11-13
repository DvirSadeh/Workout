package workout.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import workout.domain.BodyWeightPoint
import workout.domain.BodyWeightSeries
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException

enum class FitWeightStatus {
    READY,
    NEED_PERMISSION,
    NEED_UPDATE,
    UNAVAILABLE,
    FAILED,
}

data class FitWeightReading(
    val status: FitWeightStatus,
    val points: List<BodyWeightPoint>,
)

/**
 * Body weight that Google Fit has shared into Health Connect.
 * Fit writes weight there when the Fit app's Health Connect sync is on.
 */
class FitWeights(private val context: Context) {
    suspend fun read(): FitWeightReading {
        when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
            HealthConnectClient.SDK_UNAVAILABLE ->
                return FitWeightReading(FitWeightStatus.UNAVAILABLE, emptyList())
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                return FitWeightReading(FitWeightStatus.NEED_UPDATE, emptyList())
            HealthConnectClient.SDK_AVAILABLE -> Unit
            else -> return FitWeightReading(FitWeightStatus.UNAVAILABLE, emptyList())
        }
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (readPermission !in granted) {
                return FitWeightReading(FitWeightStatus.NEED_PERMISSION, emptyList())
            }
            val days = if (HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted) 365L else 30L
            val end = Instant.now()
            val start = end.minus(days, ChronoUnit.DAYS)
            val readings = mutableListOf<Pair<Instant, Double>>()
            var pageToken: String? = null
            val seen = mutableSetOf<String>()
            var pages = 0
            while (pages < 20) {
                pages += 1
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageToken = pageToken,
                    ),
                )
                response.records.forEach { record ->
                    readings += record.time to record.weight.inKilograms
                }
                val next = response.pageToken
                if (next.isNullOrEmpty() || !seen.add(next)) break
                pageToken = next
            }
            FitWeightReading(
                FitWeightStatus.READY,
                BodyWeightSeries.daily(readings, ZoneId.systemDefault()),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FitWeightReading(FitWeightStatus.FAILED, emptyList())
        }
    }

    fun openProviderUpdate() {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"),
        ).apply {
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(market) }.isSuccess) return
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(web) }
    }

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
        val readPermission: String = HealthPermission.getReadPermission(WeightRecord::class)
        val permissions: Set<String> = setOf(
            readPermission,
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        )
    }
}
