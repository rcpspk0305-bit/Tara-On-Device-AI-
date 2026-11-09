package dev.tara.androidactions

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import dev.tara.domain.TaraAction

/** Intent creation is separate from launching so device tests can inspect handoffs without executing them. */
object ActionIntents {
    fun dial(action: TaraAction.DialNumber): Intent = Intent(
        Intent.ACTION_DIAL,
        Uri.fromParts("tel", action.number.value, null),
    )

    fun sms(action: TaraAction.ComposeSms): Intent = Intent(
        Intent.ACTION_SENDTO,
        Uri.fromParts("smsto", action.recipient.value, null),
    ).putExtra("sms_body", action.body)

    fun alarm(action: TaraAction.SetAlarm): Intent = Intent(AlarmClock.ACTION_SET_ALARM)
        .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
        .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
        .putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)

    fun timer(action: TaraAction.SetTimer): Intent = Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
        .putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
}
