package dev.tara.androidactions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tara.domain.ActionResult
import dev.tara.domain.PhoneNumber
import dev.tara.domain.TaraAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidHandoffTest {
    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var recorded: Intent? = null
        var unavailable = false
        var denied = false
        override fun getApplicationContext(): Context = this
        override fun startActivity(intent: Intent) {
            if (unavailable) throw ActivityNotFoundException("Test fixture")
            if (denied) throw SecurityException("Test fixture")
            recorded = intent
        }
    }

    @Test fun missingExternalApplicationIsReportedAsUnsupported() = runBlocking {
        val context = RecordingContext(ApplicationProvider.getApplicationContext()).apply { unavailable = true }
        AndroidPlatformActions(context).use { actions ->
            assertTrue(actions.dial(TaraAction.DialNumber(PhoneNumber("+919876543210"))) is ActionResult.Unsupported)
        }
        assertNull(context.recorded)
    }

    @Test fun revokedExternalAccessDoesNotReportSuccess() = runBlocking {
        val context = RecordingContext(ApplicationProvider.getApplicationContext()).apply { denied = true }
        AndroidPlatformActions(context).use { actions ->
            assertTrue(actions.timer(TaraAction.SetTimer(60)) is ActionResult.Failure)
        }
        assertNull(context.recorded)
    }

    @Test fun invalidNumbersNeverReachAnAndroidIntent() = runBlocking {
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        AndroidPlatformActions(context).use { actions ->
            assertTrue(actions.dial(TaraAction.DialNumber(PhoneNumber("*#06#"))) is ActionResult.Failure)
            assertTrue(actions.dial(TaraAction.DialNumber(PhoneNumber("123;456"))) is ActionResult.Failure)
            assertTrue(actions.sms(TaraAction.ComposeSms(PhoneNumber("+919876543210"), " ")) is ActionResult.Failure)
        }
        assertNull(context.recorded)
    }

    @Test fun smsReportsOnlyComposerHandoffAndPreservesMessage() = runBlocking {
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        AndroidPlatformActions(context).use { actions ->
            val result = actions.sms(TaraAction.ComposeSms(PhoneNumber("+919876543210"), "Exact message"))
            assertTrue(result is ActionResult.ExternalHandoff)
            assertEquals(Intent.ACTION_SENDTO, context.recorded?.action)
            assertEquals("Exact message", context.recorded?.getStringExtra("sms_body"))
            assertTrue(context.recorded!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
    }
}
