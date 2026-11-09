package dev.tara.androidactions

import android.content.Intent
import android.provider.AlarmClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tara.domain.PhoneNumber
import dev.tara.domain.TaraAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** These tests inspect intents only; they never call, send, lock or touch hardware. */
@RunWith(AndroidJUnit4::class)
class ActionIntentsTest {
    @Test fun dialAlwaysUsesDialerAndNotDirectCall() {
        val intent = ActionIntents.dial(TaraAction.DialNumber(PhoneNumber("112")))
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel", intent.data?.scheme)
        assertEquals("112", intent.data?.schemeSpecificPart)
    }

    @Test fun smsComposerPreservesExactRecipientAndBody() {
        val intent = ActionIntents.sms(TaraAction.ComposeSms(PhoneNumber("+919876543210"), "Meet at 7:30 — café?"))
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto", intent.data?.scheme)
        assertEquals("+919876543210", intent.data?.schemeSpecificPart)
        assertEquals("Meet at 7:30 — café?", intent.getStringExtra("sms_body"))
    }

    @Test fun alarmKeepsClockUiVisibleAndUsesTypedLocalTime() {
        val intent = ActionIntents.alarm(TaraAction.SetAlarm(19, 30))
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(19, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }

    @Test fun timerKeepsClockUiVisibleAndUsesSeconds() {
        val intent = ActionIntents.timer(TaraAction.SetTimer(300))
        assertEquals(AlarmClock.ACTION_SET_TIMER, intent.action)
        assertEquals(300, intent.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }
}
