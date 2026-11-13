package workout.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.round

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

    /**
     * One point per Monday–Sunday week: the mean of that week's daily weights,
     * rounded to a tenth of a kilogram, on the last day that week was weighed.
     */
    fun weekAverage(points: List<BodyWeightPoint>): List<BodyWeightPoint> {
        if (points.isEmpty()) return emptyList()
        return points
            .groupBy { it.date.with(DayOfWeek.MONDAY) }
            .entries
            .sortedBy { it.key }
            .map { (_, week) ->
                val ordered = week.sortedBy { it.date }
                val mean = ordered.sumOf { it.kilograms } / ordered.size
                BodyWeightPoint(ordered.last().date, round(mean * 10.0) / 10.0)
            }
    }
}
