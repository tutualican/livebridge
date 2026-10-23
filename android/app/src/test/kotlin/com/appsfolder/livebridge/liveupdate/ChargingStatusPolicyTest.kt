package com.appsfolder.livebridge.liveupdate

import org.junit.Assert.*
import org.junit.Test

class ChargingStatusPolicyTest {
    @Test fun hidesWhenUnplugged() {
        assertFalse(ChargingStatusPolicy.shouldShow(plugged = false, percent = 42))
        assertFalse(ChargingStatusPolicy.shouldShow(plugged = false, percent = 100))
    }

    @Test fun showsWhileCharging() {
        assertTrue(ChargingStatusPolicy.shouldShow(plugged = true, percent = 1))
        assertTrue(ChargingStatusPolicy.shouldShow(plugged = true, percent = 99))
    }

    @Test fun hidesWhenFull() {
        assertFalse(ChargingStatusPolicy.shouldShow(plugged = true, percent = 100))
        assertTrue(ChargingStatusPolicy.isFullyCharged(100))
        assertFalse(ChargingStatusPolicy.isFullyCharged(99))
    }

    @Test fun showsWithoutLevelWhenPluggedIn() {
        assertTrue(ChargingStatusPolicy.shouldShow(plugged = true, percent = ChargingStatusPolicy.UNKNOWN_PERCENT))
    }

    @Test fun formatsRemainingTime() {
        assertEquals("45 min", ChargingStatusPolicy.formatRemaining(45 * 60_000L, "h", "min"))
        assertEquals("1 h 12 min", ChargingStatusPolicy.formatRemaining(72 * 60_000L, "h", "min"))
        assertEquals("2 h", ChargingStatusPolicy.formatRemaining(120 * 60_000L, "h", "min"))
        assertNull(ChargingStatusPolicy.formatRemaining(-1L, "h", "min"))
        assertNull(ChargingStatusPolicy.formatRemaining(0L, "h", "min"))
    }
}
