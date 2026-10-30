package com.blacksingularities.openamp.wear

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.ui.platform.LocalContext

/** Kolejka z telefonu: dotknięcie utworu przeskakuje do niego. */
@Composable
fun QueueScreen(state: NowPlaying, onBack: () -> Unit) {
    val context = LocalContext.current
    ScalingLazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { ListHeader { Text(stringResource(R.string.queue)) } }
        if (state.queue.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.queue_empty),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption2,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(state.queue) { item ->
            val current = item.id == state.trackId
            Chip(
                onClick = {
                    WearPhone.send(context, WearProtocol.CMD_SKIP, item.id)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (current) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                label = {
                    Column {
                        Text(item.title.ifBlank { "—" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.artist.isNotBlank()) {
                            Text(
                                text = item.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.caption3,
                            )
                        }
                    }
                },
            )
        }
        item {
            Chip(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(stringResource(R.string.back)) },
            )
        }
    }
}
