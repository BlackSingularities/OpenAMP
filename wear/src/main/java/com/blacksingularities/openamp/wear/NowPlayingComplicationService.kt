package com.blacksingularities.openamp.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import androidx.core.graphics.drawable.IconCompat

/** Komplikacja na tarczę: bieżący utwór; dotknięcie otwiera pilota. */
class NowPlayingComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, getString(R.string.app_name), getString(R.string.nothing_playing))

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val (title, artist, _) = WearCache.load(this)
        val hasTrack = title.isNotBlank() || artist.isNotBlank()
        return build(
            request.complicationType,
            if (hasTrack) title else getString(R.string.nothing_playing),
            if (hasTrack) artist else getString(R.string.app_name),
        )
    }

    private fun build(type: ComplicationType, first: String, second: String): ComplicationData? {
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = MonochromaticImage.Builder(IconCompat.createWithResource(this, R.drawable.ic_note).toIcon(this)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(first.take(7).ifBlank { "—" }).build(),
                contentDescription = PlainComplicationText.Builder(first).build(),
            ).setMonochromaticImage(icon).setTapAction(tap).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(first.ifBlank { "—" }).build(),
                contentDescription = PlainComplicationText.Builder(first).build(),
            ).setTitle(PlainComplicationText.Builder(second.ifBlank { getString(R.string.app_name) }).build())
                .setSmallImage(
                    SmallImage.Builder(
                        IconCompat.createWithResource(this, R.drawable.ic_note).toIcon(this),
                        SmallImageType.ICON,
                    ).build(),
                )
                .setTapAction(tap)
                .build()

            else -> null
        }
    }
}
