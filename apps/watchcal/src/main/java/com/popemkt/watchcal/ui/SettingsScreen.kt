package com.popemkt.watchcal.ui

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
 * Plays a test sound, cycling through playback paths/usages on each tap —
 * bisects what the OEM lets third-party audio reach the speaker with
 * (specs/learnings.md). The label names the variant about to be tested and
 * reports whether the player was even created.
 */
@Composable
private fun SoundCheckChip() {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    var lastResult by remember { mutableStateOf("") }
    val variant = TEST_VARIANTS[index % TEST_VARIANTS.size]
    Chip(
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(),
        label = { Text("Test: ${variant.first}$lastResult") },
        onClick = {
            val ok = variant.second(context)
            lastResult = if (ok) " ✓" else " ✗"
            index++
        },
    )
}

private fun mediaPlayerVariant(usage: Int): (android.content.Context) -> Boolean = { context ->
    val attributes = AudioAttributes.Builder()
        .setUsage(usage)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    val player = MediaPlayer.create(context, R.raw.watchcal_alarm, attributes, 0)
    player?.apply {
        setVolume(1f, 1f)
        setOnCompletionListener { it.release() }
        start()
    } != null
}

private val TEST_VARIANTS: List<Pair<String, (android.content.Context) -> Boolean>> = listOf(
    "MP-ALARM" to mediaPlayerVariant(AudioAttributes.USAGE_ALARM),
    "MP-MEDIA" to mediaPlayerVariant(AudioAttributes.USAGE_MEDIA),
    "TONEGEN" to { _ ->
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
        }.isSuccess
    },
    "RINGTONE-API" to { context ->
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            RingtoneManager.getRingtone(context, uri)?.play() != null
        }.getOrDefault(false)
    },
    "NOTIF-SOUND" to { context ->
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play() != null
        }.getOrDefault(false)
    },
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
