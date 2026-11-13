package workout.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class BodyWeightSeriesTest {
    private val utc = ZoneOffset.UTC

    @Test
    fun newestReadingOfADayWinsAndDaysStayInOrder() {
        val day = LocalDate.of(2026, 11, 1)
        val next = day.plusDays(1)
        val points = BodyWeightSeries.daily(
            listOf(
                day.atTime(8, 0).toInstant(utc) to 80.0,
                next.atTime(9, 0).toInstant(utc) to 81.0,
                day.atTime(21, 0).toInstant(utc) to 80.4,
                day.atTime(7, 0).toInstant(utc) to 79.0,
            ),
            utc,
        )
        assertEquals(listOf(day, next), points.map { it.date })
        assertEquals(listOf(80.4, 81.0), points.map { it.kilograms })
    }

    @Test
    fun nonPositiveReadingsAreDropped() {
        val day = LocalDate.of(2026, 11, 2)
        val points = BodyWeightSeries.daily(
            listOf(
                day.atTime(8, 0).toInstant(utc) to 0.0,
                day.atTime(9, 0).toInstant(utc) to -1.0,
                day.atTime(10, 0).toInstant(utc) to Double.NaN,
                day.atTime(11, 0).toInstant(utc) to Double.POSITIVE_INFINITY,
            ),
            utc,
        )
        assertEquals(emptyList(), points)
    }

    @Test
    fun theCalendarDayFollowsTheZone() {
        val instant = Instant.parse("2026-11-02T01:30:00Z")
        val points = BodyWeightSeries.daily(
            listOf(instant to 77.5),
            ZoneId.of("America/Los_Angeles"),
        )
        assertEquals(LocalDate.of(2026, 11, 1), points.single().date)
        assertEquals(77.5, points.single().kilograms)
    }

    @Test
    fun weekAverageIsTheMeanOfEachMondayWeek() {
        val monday = LocalDate.of(2026, 11, 2)
        val points = listOf(
            BodyWeightPoint(monday, 80.0),
            BodyWeightPoint(monday.plusDays(2), 82.2),
            BodyWeightPoint(monday.plusDays(7), 78.0),
        )
        val weeks = BodyWeightSeries.weekAverage(points)
        assertEquals(listOf(monday.plusDays(2), monday.plusDays(7)), weeks.map { it.date })
        assertEquals(listOf(81.1, 78.0), weeks.map { it.kilograms })
    }

    @Test
    fun aSingleDayIsItsOwnWeekAverage() {
        val day = LocalDate.of(2026, 11, 4)
        val points = listOf(BodyWeightPoint(day, 76.4))
        assertEquals(points, BodyWeightSeries.weekAverage(points))
    }
}
