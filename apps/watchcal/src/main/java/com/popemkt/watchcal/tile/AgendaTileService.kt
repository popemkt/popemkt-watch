package com.popemkt.watchcal.tile

import android.Manifest
import android.content.pm.PackageManager
import androidx.concurrent.futures.ResolvableFuture
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.popemkt.watchcal.App
import com.popemkt.watchcal.domain.AgendaEntry
import com.popemkt.watchcal.domain.AgendaPolicy
import com.popemkt.watchcal.domain.ReminderDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The Wear tile. Renders a glanceable, cyclable view of the upcoming events — read-only,
 * scheduling nothing and never calling `ReminderCoordinator.refresh()`, so it adds zero to
 * the wakeup budget (specs/01-architecture.md § Tile surface; battery rule).
 */
class AgendaTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val app get() = applicationContext as App
    private val cursorStore by lazy { TileCursorStore(applicationContext) }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<Tile> {
        val future = ResolvableFuture.create<Tile>()
        scope.launch {
            try {
                future.set(buildTile(requestParams))
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<Resources> {
        val future = ResolvableFuture.create<Resources>()
        future.set(Resources.Builder().setVersion(RESOURCES_VERSION).build())
        return future
    }

    private suspend fun buildTile(requestParams: RequestBuilders.TileRequest): Tile {
        val model = resolveModel(requestParams)
        val layout = AgendaTileRenderer.render(this, requestParams.deviceConfiguration, model)
        return Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(Timeline.fromLayoutElement(layout))
            .build()
    }

    /**
     * Permission gate → load forward events → step + clamp the cursor, and — when the tap was
     * the card — cycle the focused event's state through [ReminderCoordinator] before redrawing.
     */
    private suspend fun resolveModel(requestParams: RequestBuilders.TileRequest): TileModel {
        if (!hasCalendarPermission()) return TileModel.NeedsPermission

        val tappedId = requestParams.currentState.lastClickableId
        var entries = forwardEntries()
        if (entries.isEmpty()) return TileModel.Empty

        val index = steppedCursor(tappedId, entries.lastIndex)
        if (tappedId == AgendaTileRenderer.ID_TOGGLE) {
            cycleState(entries[index])
            entries = forwardEntries() // re-read so the card reflects the new state
        }
        return TileModel.Card(entries[index], index, entries.size)
    }

    private suspend fun cycleState(entry: AgendaEntry) {
        val key = entry.instance.instanceKey
        app.reminderCoordinator.applyTapAction(key, AgendaPolicy.tapAction(entry.state))
    }

    private suspend fun forwardEntries(): List<AgendaEntry> {
        val now = System.currentTimeMillis()
        val instances = app.calendarSource
            .instances(now - ReminderDefaults.AGENDA_LOOKBACK_MILLIS, now + ReminderDefaults.AGENDA_FORWARD_MILLIS)
        val states = app.stateStore.states()
        return AgendaPolicy.entries(instances, states)
    }

    private suspend fun steppedCursor(lastClickableId: String, lastIndex: Int): Int {
        val stored = cursorStore.cursor()
        val stepped = when (lastClickableId) {
            AgendaTileRenderer.ID_PREV -> stored - 1
            AgendaTileRenderer.ID_NEXT -> stored + 1
            else -> stored
        }
        val clamped = stepped.coerceIn(0, lastIndex)
        cursorStore.setCursor(clamped)
        return clamped
    }

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}
