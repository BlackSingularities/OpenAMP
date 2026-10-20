package com.blacksingularities.openamp.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.i18n.plural
import com.blacksingularities.openamp.i18n.tr
import java.util.Locale

/** Właściwości pliku: okładka, metadane, parametry dźwięku, plik na dysku i historia słuchania. */
@Composable
fun TrackDetailsDialog(vm: MainViewModel, track: Track, onDismiss: () -> Unit) {
    val data by vm.userData.collectAsState()
    val context = LocalContext.current
    val info by produceState<FileInfo?>(null, track.id) { value = vm.fileInfoFor(track) }
    val stat = data.stats[track.id]
    val art = rememberArtwork(track, 600)
    val tint = remember(art) { art?.let { runCatching { com.blacksingularities.openamp.ui.theme.vibrantColor(it) }.getOrNull() }?.let { Color(it) } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(14.dp).widthIn(max = 520.dp).fillMaxWidth().heightIn(max = 720.dp)
                .clip(RoundedCornerShape(24.dp)).background(Compact.Menu),
        ) {
            // Nagłówek w kolorze okładki
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf((tint ?: Color.White).copy(alpha = 0.28f), Color.Transparent)))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track, Modifier.size(84.dp), size = 600, shape = RoundedCornerShape(16.dp))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(track.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                        Text(track.artist, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(track.album, track.year.takeIf { it > 0 }?.toString()).joinToString(" • "), color = Compact.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Chip(track.format)
                            info?.let { i -> if (i.sampleRate > 0) Chip(String.format(Locale.ROOT, "%.1f kHz", i.sampleRate / 1000f).replace(".0 ", " ")) }
                            info?.let { i -> if (i.bitDepth > 0) Chip("${i.bitDepth}-bit") }
                            info?.let { i -> if (i.bitrateKbps > 0) Chip("${i.bitrateKbps} kb/s") }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SetCard(tr("Utwór")) {
                    InfoRow(Icons.Rounded.Person, tr("Wykonawca"), track.artist)
                    if (track.albumArtist.isNotBlank() && track.albumArtist != track.artist) InfoRow(Icons.Rounded.Person, tr("Wykonawca albumu"), track.albumArtist)
                    InfoRow(Icons.Rounded.Album, tr("Album"), track.album)
                    if (track.genre.isNotBlank()) InfoRow(Icons.Rounded.Style, tr("Gatunek"), track.genre)
                    if (track.composer.isNotBlank()) InfoRow(Icons.Rounded.Edit, tr("Kompozytor"), track.composer)
                    val num = listOfNotNull(
                        track.trackNumber.takeIf { it > 0 }?.let { tr("utwór {0}", it) },
                        track.discNumber.takeIf { it > 1 }?.let { tr("płyta {0}", it) },
                    ).joinToString(", ")
                    if (num.isNotBlank()) InfoRow(Icons.Rounded.Numbers, tr("Numer"), num)
                    InfoRow(Icons.Rounded.Timer, tr("Czas trwania"), formatTime(track.durationMs))
                }
                SetCard(tr("Dźwięk")) {
                    val i = info
                    if (i == null) InfoRow(Icons.Rounded.GraphicEq, tr("Parametry"), tr("Wczytywanie…"))
                    else {
                        InfoRow(Icons.Rounded.AudioFile, tr("Kodek"), listOf(i.format, track.mime).filter { it.isNotBlank() }.distinct().joinToString(" • "))
                        if (i.sampleRate > 0) InfoRow(Icons.Rounded.Waves, tr("Częstotliwość próbkowania"), String.format(Locale.ROOT, "%,d Hz", i.sampleRate).replace(',', ' '))
                        if (i.bitDepth > 0) InfoRow(Icons.Rounded.GraphicEq, tr("Głębia bitowa"), "${i.bitDepth} bit")
                        if (i.channels > 0) InfoRow(Icons.Rounded.GraphicEq, tr("Kanały"), when (i.channels) { 1 -> tr("Mono"); 2 -> tr("Stereo"); else -> i.channels.toString() })
                        if (i.bitrateKbps > 0) InfoRow(Icons.Rounded.Speed, tr("Przepływność"), "${i.bitrateKbps} kb/s")
                    }
                }
                SetCard(tr("Plik")) {
                    InfoRow(Icons.AutoMirrored.Rounded.InsertDriveFile, tr("Nazwa"), track.path.substringAfterLast('/'))
                    InfoRow(Icons.Rounded.Folder, tr("Folder"), track.folder)
                    InfoRow(Icons.Rounded.SdStorage, tr("Rozmiar"), formatSize(track.size))
                    InfoRow(Icons.Rounded.CalendarMonth, tr("Data dodania"), formatDate(track.dateAdded))
                }
                SetCard(tr("Słuchanie")) {
                    InfoRow(Icons.Rounded.PlayCircle, tr("Odtworzenia"), plural(stat?.count ?: 0, "{0} raz", "{0} razy", "{0} razy"))
                    if ((stat?.listenMs ?: 0) > 0) InfoRow(Icons.Rounded.Timer, tr("Czas słuchania"), formatTime(stat?.listenMs ?: 0))
                    InfoRow(Icons.Rounded.History, tr("Ostatnio"), formatDate(stat?.lastPlayed ?: 0))
                    InfoRow(Icons.Rounded.Favorite, tr("Ulubiony"), if (track.id in data.favorites) tr("Tak") else tr("Nie"))
                }
                Spacer(Modifier.size(4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(null, {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("path", track.path))
                    vm.message(tr("Skopiowano ścieżkę"))
                }, icon = Icons.Rounded.ContentCopy, label = tr("Kopiuj ścieżkę"), compact = true)
                PillButton(null, { shareTrack(context, track) }, icon = Icons.Rounded.Share, label = tr("Udostępnij"), compact = true)
                PillButton(null, { onDismiss(); vm.deleteRequest = listOf(track) }, icon = Icons.Rounded.Delete, label = tr("Usuń z urządzenia"), compact = true)
                Spacer(Modifier.weight(1f))
                PillButton(tr("Zamknij"), onDismiss, active = true, compact = true)
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp))
}

fun shareTrack(context: Context, track: Track) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType(track.mime.ifEmpty { "audio/*" })
        .putExtra(android.content.Intent.EXTRA_STREAM, track.uri)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(android.content.Intent.createChooser(send, track.title)) }
}

/**
 * Usuwanie plików z urządzenia: potwierdzenie w aplikacji, potem systemowe okno zgody
 * (Android 11+: prośba MediaStore; Android 10: wyjątek z akcją użytkownika i ponowna próba).
 */
@Composable
fun DeleteHost(vm: MainViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf<List<Track>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val list = granted
        granted = emptyList()
        if (r.resultCode != Activity.RESULT_OK || list.isEmpty()) return@rememberLauncherForActivityResult
        if (Build.VERSION.SDK_INT < 30) list.forEach { t -> runCatching { context.contentResolver.delete(t.uri, null, null) } }
        vm.onDeleted(list.map { it.id })
    }
    val req = vm.deleteRequest ?: return
    AlertDialog(
        onDismissRequest = { vm.deleteRequest = null },
        title = { Text(if (req.size == 1) tr("Usunąć plik?") else tr("Usunąć {0} plików?", req.size)) },
        text = {
            Text(
                (if (req.size == 1) "„${req[0].title}” – ${req[0].artist}\n\n" else "") +
                    tr("Plik zostanie trwale usunięty z urządzenia i zniknie ze wszystkich list."),
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                vm.deleteRequest = null
                granted = req
                runCatching {
                    if (Build.VERSION.SDK_INT >= 30) {
                        val pi = MediaStore.createDeleteRequest(context.contentResolver, req.map { it.uri })
                        launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    } else {
                        try {
                            req.forEach { context.contentResolver.delete(it.uri, null, null) }
                            granted = emptyList()
                            vm.onDeleted(req.map { it.id })
                        } catch (e: RecoverableSecurityException) {
                            launcher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                        }
                    }
                }.onFailure { vm.message(tr("Nie udało się usunąć: {0}", it.message ?: "")) }
            }) { Text(tr("Usuń"), color = Color(0xFFFF6B6B)) }
        },
        dismissButton = { TextButton(onClick = { vm.deleteRequest = null }) { Text(tr("Anuluj")) } },
    )
}
