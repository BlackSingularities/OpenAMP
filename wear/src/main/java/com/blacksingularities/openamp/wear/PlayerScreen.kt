package com.blacksingularities.openamp.wear

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

/** Ekran pilota: okładka, tytuł, pasek postępu i przyciski. */
@Composable
fun PlayerScreen(state: NowPlaying, art: Bitmap?, onQueue: () -> Unit) {
    val context = LocalContext.current
    val accent = if (state.accent != 0) Color(state.accent) else MaterialTheme.colors.primary

    // Pozycja doliczana lokalnie – telefon przysyła migawkę tylko przy zmianach.
    var position by remember { mutableLongStateOf(state.position()) }
    LaunchedEffect(state.playing, state.updatedAt, state.positionMs) {
        position = state.position()
        while (state.playing) {
            delay(500)
            position = state.position()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!state.known) {
            Text(stringResource(R.string.waiting), textAlign = TextAlign.Center, style = MaterialTheme.typography.body2)
            return@Column
        }
        if (!state.hasTrack) {
            Text(stringResource(R.string.nothing_playing), textAlign = TextAlign.Center, style = MaterialTheme.typography.title3)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.no_phone), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2)
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_note),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = accent,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.button,
                )
                if (state.artist.isNotBlank()) {
                    Text(
                        text = state.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SeekBar(position, state.durationMs, accent) { ms -> WearPhone.send(context, WearProtocol.CMD_SEEK, ms) }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(time(position), style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.onSurfaceVariant)
            Text(time(state.durationMs), style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactButton(
                onClick = { WearPhone.send(context, WearProtocol.CMD_PREV) },
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Icon(painterResource(R.drawable.ic_prev), stringResource(R.string.previous), modifier = Modifier.size(18.dp))
            }
            Button(
                onClick = { WearPhone.send(context, WearProtocol.CMD_TOGGLE) },
                modifier = Modifier.size(52.dp),
                colors = ButtonDefaults.primaryButtonColors(backgroundColor = accent),
            ) {
                Icon(
                    painter = painterResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = stringResource(if (state.playing) R.string.pause else R.string.play),
                    modifier = Modifier.size(26.dp),
                    tint = Color.Black,
                )
            }
            CompactButton(
                onClick = { WearPhone.send(context, WearProtocol.CMD_NEXT) },
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Icon(painterResource(R.drawable.ic_next), stringResource(R.string.next), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactButton(
                onClick = { WearPhone.send(context, WearProtocol.CMD_FAVORITE) },
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Icon(
                    painter = painterResource(if (state.favorite) R.drawable.ic_heart else R.drawable.ic_heart_outline),
                    contentDescription = stringResource(R.string.favorite),
                    modifier = Modifier.size(16.dp),
                    tint = if (state.favorite) accent else MaterialTheme.colors.onSurfaceVariant,
                )
            }
            CompactButton(
                onClick = { WearPhone.send(context, WearProtocol.CMD_SHUFFLE) },
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = stringResource(R.string.shuffle),
                    modifier = Modifier.size(16.dp),
                    tint = if (state.shuffle) accent else MaterialTheme.colors.onSurfaceVariant,
                )
            }
            CompactButton(
                onClick = { WearPhone.send(context, WearProtocol.CMD_REPEAT) },
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Icon(
                    painter = painterResource(if (state.repeat == "ONE") R.drawable.ic_repeat_one else R.drawable.ic_repeat),
                    contentDescription = stringResource(R.string.repeat),
                    modifier = Modifier.size(16.dp),
                    tint = if (state.repeat == "OFF") MaterialTheme.colors.onSurfaceVariant else accent,
                )
            }
            CompactButton(onClick = onQueue, colors = ButtonDefaults.secondaryButtonColors()) {
                Text("≡", style = MaterialTheme.typography.caption1)
            }
        }

        if (state.volumeMax > 0) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactButton(
                    onClick = { WearPhone.send(context, WearProtocol.CMD_VOLUME, -1) },
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) {
                    Text("−", style = MaterialTheme.typography.caption1)
                }
                Text(
                    text = "${state.volume * 100 / state.volumeMax}%",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
                CompactButton(
                    onClick = { WearPhone.send(context, WearProtocol.CMD_VOLUME, 1) },
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) {
                    Text("+", style = MaterialTheme.typography.caption1)
                }
            }
        }
    }
}

/** Pasek postępu z przewijaniem dotknięciem. */
@Composable
private fun SeekBar(position: Long, durationMs: Long, accent: Color, onSeek: (Long) -> Unit) {
    val track = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.3f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) onSeek((durationMs * offset.x / size.width.toFloat()).toLong().coerceIn(0, durationMs))
                }
            },
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(color = track, cornerRadius = r)
        val f = if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        if (f > 0f) {
            drawRoundRect(
                color = accent,
                topLeft = Offset.Zero,
                size = Size(size.width * f, size.height),
                cornerRadius = r,
            )
        }
    }
}

internal fun time(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
