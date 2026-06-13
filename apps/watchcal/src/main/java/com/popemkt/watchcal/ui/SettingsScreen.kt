package com.popemkt.watchcal.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.popemkt.watchcal.R
import com.popemkt.watchcal.domain.ReminderDefaults
import java.util.concurrent.TimeUnit

/**
 * Snooze-interval editor (specs/00-product.md § Settings): minutes + seconds
 * steppers over one global value, clamped by the domain rule on every change,
 * plus a one-tap test of the bundled alert tone.
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

    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Settings") } }
            item { Text("Snooze interval", style = MaterialTheme.typography.title3) }
            item {
                StepperRow(
                    label = "$minutes min",
                    onDecrement = { update(minutes - 1, seconds) },
                    onIncrement = { update(minutes + 1, seconds) },
                )
            }
            item {
                StepperRow(
                    label = "$seconds sec",
                    onDecrement = { update(minutes, seconds - SECONDS_STEP) },
                    onIncrement = { update(minutes, seconds + SECONDS_STEP) },
                )
            }
            item {
                Text(
                    "Comes back every $minutes m $seconds s",
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
            }
            item { TestSoundChip() }
        }
    }
}

/** Plays the bundled alert tone once on the alarm stream (a no-op where the OEM exposes no app audio output). */
@Composable
private fun TestSoundChip() {
    val context = LocalContext.current
    Chip(
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(),
        icon = { Text("♪", style = MaterialTheme.typography.title3) },
        label = { Text("Test sound") },
        onClick = { playAlarmTone(context) },
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
        Button(
            onClick = onDecrement,
            modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
        ) { Text("−") }
        Text(label, style = MaterialTheme.typography.body1)
        Button(
            onClick = onIncrement,
            modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
        ) { Text("+") }
    }
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_STEP = 10L
