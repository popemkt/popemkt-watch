package com.popemkt.watchcal.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.popemkt.watchcal.R
import com.popemkt.watchcal.domain.ReminderDefaults
import java.util.concurrent.TimeUnit

/**
 * Snooze-interval editor (specs/00-product.md § Settings): minutes + seconds steppers over one
 * global value, clamped by the domain rule on every change, plus a one-tap test of the bundled
 * alert tone. Material 3 [TransformingLazyColumn] so the content morphs to the round display.
 */
@Composable
fun SettingsScreen(snoozeIntervalMillis: Long, onIntervalChange: (Long) -> Unit) {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(snoozeIntervalMillis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(snoozeIntervalMillis) % SECONDS_PER_MINUTE

    fun update(newMinutes: Long, newSeconds: Long) {
        onIntervalChange(
            ReminderDefaults.clampSnoozeInterval(
                TimeUnit.MINUTES.toMillis(newMinutes) + TimeUnit.SECONDS.toMillis(newSeconds),
            ),
        )
    }

    val columnState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
            item(key = "header") {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text("Settings") }
            }
            item(key = "title") { Text("Snooze interval", style = MaterialTheme.typography.titleMedium) }
            item(key = "min") {
                StepperRow(
                    label = "$minutes min",
                    onDecrement = { update(minutes - 1, seconds) },
                    onIncrement = { update(minutes + 1, seconds) },
                )
            }
            item(key = "sec") {
                StepperRow(
                    label = "$seconds sec",
                    onDecrement = { update(minutes, seconds - SECONDS_STEP) },
                    onIncrement = { update(minutes, seconds + SECONDS_STEP) },
                )
            }
            item(key = "summary") {
                Text(
                    "First snooze: $minutes m $seconds s",
                    style = MaterialTheme.typography.bodyExtraSmall,
                    textAlign = TextAlign.Center,
                )
            }
            item(key = "test") {
                TestSoundChip(
                    Modifier.fillMaxWidth().transformedHeight(this, spec),
                    SurfaceTransformation(spec),
                )
            }
        }
    }
}

/** Plays the bundled alert tone once on the alarm stream (a no-op where the OEM exposes no app audio output). */
@Composable
private fun TestSoundChip(modifier: Modifier, transformation: SurfaceTransformation) {
    val context = LocalContext.current
    FilledTonalButton(
        onClick = { playAlarmTone(context) },
        modifier = modifier,
        transformation = transformation,
        label = { Text("Test sound") },
        icon = { Text("♪", style = MaterialTheme.typography.titleMedium) },
    )
}

private fun playAlarmTone(context: Context) {
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    MediaPlayer.create(context, R.raw.watchcal_alarm, attributes, 0)?.apply {
        setVolume(1f, 1f)
        setOnCompletionListener { it.release() }
        start()
    }
}

@Composable
private fun StepperRow(label: String, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(onClick = onDecrement) { Text("−") }
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = onIncrement) { Text("+") }
    }
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_STEP = 10L
