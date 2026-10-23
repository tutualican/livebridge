package com.appsfolder.livebridge.liveupdate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherSnapshotTest {
    @Test
    fun snapshotRoundTripsThroughJson() {
        val snapshot = WeatherSnapshot(
            city = "Istanbul",
            temperatureC = 21,
            apparentC = 20,
            weatherCode = 2,
            maxC = 24,
            minC = 14,
            updatedAtMs = 1_700_000_000_000L
        )

        val decoded = WeatherSnapshot.fromJson(snapshot.toJson())

        assertEquals(snapshot, decoded)
    }

    @Test
    fun optionalValuesSurviveRoundTrip() {
        val snapshot = WeatherSnapshot(
            city = null,
            temperatureC = 7,
            apparentC = null,
            weatherCode = 61,
            maxC = null,
            minC = null,
            updatedAtMs = 0L
        )

        val decoded = WeatherSnapshot.fromJson(snapshot.toJson())

        assertEquals(snapshot, decoded)
    }

    @Test
    fun invalidJsonIsIgnored() {
        assertNull(WeatherSnapshot.fromJson(null))
        assertNull(WeatherSnapshot.fromJson(""))
        assertNull(WeatherSnapshot.fromJson("not json"))
    }
}
