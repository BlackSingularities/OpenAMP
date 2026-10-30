package com.blacksingularities.openamp.wear

import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import android.content.ComponentName
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Migawki z telefonu odbierane także przy zgaszonym ekranie: zapisują ostatni utwór
 * i odświeżają kafelek oraz komplikację na tarczy.
 */
class WearDataService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        var changed = false
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == WearProtocol.PATH_STATE) {
                WearPhone.attach(applicationContext)
                val map = DataMapItem.fromDataItem(e.dataItem).dataMap
                WearPhone.consume(map)
                WearCache.save(applicationContext, WearPhone.state.value)
                changed = true
            }
        }
        if (changed) refreshFaces(applicationContext)
    }

    companion object {
        /** Kafelek i komplikacja czytają zapisaną migawkę – po zmianie trzeba je obudzić. */
        fun refreshFaces(context: Context) {
            runCatching { TileService.getUpdater(context).requestUpdate(NowPlayingTileService::class.java) }
            runCatching {
                ComplicationDataSourceUpdateRequester
                    .create(context, ComponentName(context, NowPlayingComplicationService::class.java))
                    .requestUpdateAll()
            }
        }
    }
}

/** Ostatnia migawka w pamięci trwałej – kafelek i komplikacja działają bez uruchomionej aplikacji. */
object WearCache {
    private const val PREFS = "openamp_wear"

    fun save(context: Context, s: NowPlaying) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", s.title)
            .putString("artist", s.artist)
            .putBoolean("playing", s.playing)
            .putBoolean("hasTrack", s.hasTrack)
            .apply()
    }

    fun load(context: Context): Triple<String, String, Boolean> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("hasTrack", false)) return Triple("", "", false)
        return Triple(p.getString("title", "") ?: "", p.getString("artist", "") ?: "", p.getBoolean("playing", false))
    }
}
