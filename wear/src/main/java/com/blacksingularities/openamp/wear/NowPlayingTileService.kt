package com.blacksingularities.openamp.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Kafelek: co gra na telefonie i jeden przycisk graj / pauza. */
class NowPlayingTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val (title, artist, playing) = WearCache.load(this)
        val device = requestParams.deviceConfiguration
        val hasTrack = title.isNotBlank() || artist.isNotBlank()

        val content = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(this, if (hasTrack) title else getString(R.string.nothing_playing))
                    .setTypography(Typography.TYPOGRAPHY_BUTTON)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .setMaxLines(2)
                    .build(),
            )
            .apply {
                if (artist.isNotBlank()) {
                    addContent(
                        Text.Builder(this@NowPlayingTileService, artist)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                            .setColor(argb(0xFFB0B0B0.toInt()))
                            .setMaxLines(1)
                            .build(),
                    )
                }
            }
            .build()

        val chipLabel = when {
            !hasTrack -> getString(R.string.open_on_phone)
            playing -> getString(R.string.pause)
            else -> getString(R.string.play)
        }
        val chip = CompactChip.Builder(this, chipLabel, click(if (hasTrack) WearProtocol.CMD_TOGGLE else null), device).build()

        val layout = PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(
                Text.Builder(this, getString(R.string.app_name))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(0xFFB0B0B0.toInt()))
                    .build(),
            )
            .setContent(content)
            .setPrimaryChipContent(chip)
            .build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES)
                .setFreshnessIntervalMillis(0)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build(),
        )
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build())

    /** Bez utworu kafelek otwiera pilota, z utworem – przełącza graj / pauza. */
    private fun click(action: String?): ModifiersBuilders.Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(if (action == null) MainActivity::class.java.name else CommandActivity::class.java.name)
        if (action != null) {
            activity.addKeyToExtraMapping(
                CommandActivity.EXTRA_ACTION,
                ActionBuilders.AndroidStringExtra.Builder().setValue(action).build(),
            )
        }
        return ModifiersBuilders.Clickable.Builder()
            .setId(action ?: "open")
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
            .build()
    }

    private companion object {
        const val RESOURCES = "1"
    }
}
