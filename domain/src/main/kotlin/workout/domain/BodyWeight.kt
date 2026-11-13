package workout.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class BodyWeightPoint(val date: LocalDate, val kilograms: Double)

object BodyWeightSeries {
    /**
     * One point per calendar day in [zone]. Several weigh-ins on the same day keep the latest.
     * Zero, negative, and non-finite readings are left out.
     */
    fun daily(readings: List<Pair<Instant, Double>>, zone: ZoneId): List<BodyWeightPoint> {
        val latest = linkedMapOf<LocalDate, Pair<Instant, Double>>()
        readings.forEach { (instant, kilograms) ->
            if (!kilograms.isFinite() || kilograms <= 0.0) return@forEach
            val date = instant.atZone(zone).toLocalDate()
            val current = latest[date]
            if (current == null || !instant.isBefore(current.first)) {
                latest[date] = instant to kilograms
            }
        }
        return latest.entries
            .sortedBy { it.key }
            .map { (date, reading) -> BodyWeightPoint(date, reading.second) }
    }
}
