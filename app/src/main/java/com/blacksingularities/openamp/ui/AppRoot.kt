package com.blacksingularities.openamp.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blacksingularities.openamp.i18n.I18n
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.ui.theme.OpenAmpTheme
import com.blacksingularities.openamp.update.UpdateState

private val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun AppRoot(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val artAccent by vm.artAccent.collectAsState()
    val accent = if (s.artworkColors) artAccent ?: s.accent else s.accent
    // Odczyt stanu języka – zmiana przerysowuje całe drzewo.
    @Suppress("UNUSED_VARIABLE") val english = I18n.english

    OpenAmpTheme(accent, s.theme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val context = LocalContext.current
            var granted by remember {
                mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
                granted = res[audioPermission] == true
                if (granted) vm.rescan()
            }
            LaunchedEffect(Unit) {
                if (!granted) {
                    val perms = buildList {
                        add(audioPermission)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    launcher.launch(perms.toTypedArray())
                }
            }
            if (!granted) {
                PermissionScreen(
                    onRequest = { launcher.launch(arrayOf(audioPermission)) },
                    onSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
                return@Surface
            }
            Main(vm)
        }
    }
}

@Composable
private fun Main(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val track by vm.currentTrack.collectAsState()
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = vm.nowPlayingOpen || vm.queueOpen || vm.searchOpen || vm.stack.size > 1) { vm.back() }
    val miniHeight = if (track != null) 92.dp else 0.dp

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            vm.route to vm.stack.size,
            transitionSpec = {
                val forward = targetState.second >= initialState.second
                (slideInHorizontally(tween(260)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(260)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200)))
            },
            contentKey = { it.first },
            label = "route",
        ) { (r, _) ->
            val bottom = miniHeight
            Box(Modifier.navigationBarsPadding()) {
                when (r) {
                    Route.Library -> LibraryScreen(vm, bottom)
                    is Route.Detail -> DetailScreen(vm, r, bottom)
                    Route.Settings -> SettingsScreen(vm, bottom)
                    Route.Equalizer -> EqualizerScreen(vm, bottom)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
            AnimatedVisibility(!vm.nowPlayingOpen, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                MiniPlayer(vm)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = miniHeight))
        AnimatedVisibility(
            vm.nowPlayingOpen,
            enter = slideInVertically(tween(380)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(320)) { it } + fadeOut(tween(250)),
        ) {
            NowPlayingScreen(vm)
        }
    }
    UpdateDialog(vm)
}

@Composable
private fun UpdateDialog(vm: MainViewModel) {
    val st by vm.update.collectAsState()
    if (!vm.updateDialog) return
    val info = when (st) {
        is UpdateState.Available -> (st as UpdateState.Available).info
        is UpdateState.Downloading -> (st as UpdateState.Downloading).info
        is UpdateState.Ready -> (st as UpdateState.Ready).info
        is UpdateState.Installing -> (st as UpdateState.Installing).info
        is UpdateState.Failed -> null
        else -> { vm.updateDialog = false; return }
    }
    AlertDialog(
        onDismissRequest = { vm.updateDialog = false },
        title = { Text(info?.let { tr("Dostępna wersja {0}", it.version) } ?: tr("Aktualizacje")) },
        text = {
            Column {
                if (info != null && info.notes.isNotBlank()) {
                    Text(tr("Co nowego"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(info.notes.take(1200), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                }
                UpdateStatus(vm, st)
            }
        },
        confirmButton = { TextButton(onClick = { vm.updateDialog = false }) { Text(tr("Później")) } },
    )
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text(tr("Dostęp do muzyki"), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            tr("OpenAMP potrzebuje dostępu do plików audio na urządzeniu, aby zbudować bibliotekę."),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text(tr("Zezwól")) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSettings) { Text(tr("Otwórz ustawienia aplikacji")) }
    }
}
