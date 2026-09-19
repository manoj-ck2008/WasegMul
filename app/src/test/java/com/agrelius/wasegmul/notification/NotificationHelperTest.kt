package com.agrelius.wasegmul.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationHelperTest {

    @Test
    fun testLevelUpNotificationConstants() {
        assertEquals("eco_level_up", NotificationHelper.CHANNEL_ID_LEVEL_UP)
        assertEquals(1002, NotificationHelper.NOTIFICATION_ID_LEVEL_UP)
        assertEquals("eco_daily_report", NotificationHelper.CHANNEL_ID_DAILY_IMPACT)
    }
}
