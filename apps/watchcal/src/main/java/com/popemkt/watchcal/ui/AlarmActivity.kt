package com.popemkt.watchcal.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.ui.platform.LocalContext
import com.popemkt.watchcal.domain.ReminderDefaults
import com.popemkt.watchcal.reminders.ReminderCoordinator
import com.popemkt.watchcal.reminders.ReminderGraphOwner
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The full-screen takeover alert (specs/00-product.md § Alarm-style alert).
 * Rings + vibrates while visible; every exit path except Done is a snooze:
 * the Snooze button, back/swipe dismiss, and the bounded ring timeout.
 */
class AlarmActivity : ComponentActivity() {

    private lateinit var ringer: AlarmRinger
    private var acted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val instanceKey = intent.getStringExtra(EXTRA_INSTANCE_KEY)
        if (instanceKey == null) {
            finish()
            return
        }
        showOverLockscreen()
        ringer = AlarmRinger(this)
        autoSnoozeAfterRingTimeout(instanceKey)
        setContent {
            AlarmScreen(
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                beginMillis = intent.getLongExtra(EXTRA_BEGIN_MILLIS, 0L),
                onSnooze = { act(instanceKey) { snooze(it) } },
                onDone = { act(instanceKey) { markDone(it) } },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        ringer.start()
    }

    override fun onStop() {
        ringer.stop()
        // Back/swipe dismiss without choosing = snooze; never a silent drop.
        if (isFinishing && !acted) act(intent.getStringExtra(EXTRA_INSTANCE_KEY)) { snooze(it) }
        super.onStop()
    }

    private fun showOverLockscreen() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun autoSnoozeAfterRingTimeout(instanceKey: String) {
        lifecycleScope.launch {
            delay(ReminderDefaults.RING_TIMEOUT_MILLIS)
            act(instanceKey) { snooze(it) }
        }
    }

    private fun act(instanceKey: String?, action: suspend ReminderCoordinator.(String) -> Unit) {
        if (acted || instanceKey == null) return
        acted = true
        ringer.stop()
        val coordinator = (applicationContext as ReminderGraphOwner).reminderCoordinator
        // Detached scope: the state write must survive this activity finishing.
        CoroutineScope(Dispatchers.Default).launch { coordinator.action(instanceKey) }
        finish()
    }

    companion object {
        const val EXTRA_INSTANCE_KEY = "instance_key"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BEGIN_MILLIS = "begin_millis"

        fun intent(context: Context, instanceKey: String, title: String, beginMillis: Long): Intent =
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_INSTANCE_KEY, instanceKey)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_BEGIN_MILLIS, beginMillis)
    }
}

@Composable
private fun AlarmScreen(title: String, beginMillis: Long, onSnooze: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.title2, textAlign = TextAlign.Center, maxLines = 3)
        Text(
            DateFormat.getTimeFormat(context).format(Date(beginMillis)),
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSnooze) { Text("Zz") }
            Button(onClick = onDone) { Text("✓") }
        }
    }
}
