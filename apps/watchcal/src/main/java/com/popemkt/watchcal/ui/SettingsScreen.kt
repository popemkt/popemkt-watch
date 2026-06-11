package com.popemkt.watchcal.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.popemkt.watchcal.R
import com.popemkt.watchcal.domain.ReminderDefaults
import java.util.concurrent.TimeUnit

/**
 * Snooze-interval editor (specs/00-product.md § Settings): minutes + seconds
 * steppers over one global value, clamped by the domain rule on every change.
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

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
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
        item { Text("Comes back every $minutes m $seconds s", style = MaterialTheme.typography.caption2) }
        item { SoundCheckChip() }
    }
}

/**
 * Plays the bundled tone, cycling AudioAttributes usage on each tap — bisects
 * which usages the OEM lets third-party audio reach the speaker with
 * (specs/learnings.md). The label names the usage about to be tested.
 */
@Composable
private fun SoundCheckChip() {
    val context = LocalContext.current
    var usageIndex by remember { mutableIntStateOf(0) }
    val (usageName, usage) = TEST_USAGES[usageIndex % TEST_USAGES.size]
    Chip(
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(),
        label = { Text("Test: $usageName") },
        onClick = {
            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            MediaPlayer.create(context, R.raw.watchcal_alarm, attributes, 0)?.apply {
                setVolume(1f, 1f)
                setOnCompletionListener { it.release() }
                start()
            }
            usageIndex++
        },
    )
}

private val TEST_USAGES = listOf(
    "ALARM" to AudioAttributes.USAGE_ALARM,
    "NOTIFICATION" to AudioAttributes.USAGE_NOTIFICATION,
    "RINGTONE" to AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
    "MEDIA" to AudioAttributes.USAGE_MEDIA,
    "SONIFICATION" to AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
)

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
