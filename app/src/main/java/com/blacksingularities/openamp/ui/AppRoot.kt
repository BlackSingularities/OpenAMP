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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.blacksingularities.openamp.Graph
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
    val hints = remember { HintState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = vm.menuOpen || vm.queueOpen || vm.stack.size > 1) { vm.back() }
    val onPlayer = vm.route == Route.Player
    val bottom = 54.dp + if (track != null && !onPlayer) 66.dp else 0.dp
    val ps by vm.player.state.collectAsState()
    val autoHide = vm.settings.collectAsState().value.autoHideBar
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    var barHidden by remember { mutableStateOf(false) }
    LaunchedEffect(onPlayer, ps.isPlaying, autoHide, lastTouch, vm.menuOpen) {
        barHidden = false
        if (!onPlayer || !ps.isPlaying || !autoHide || vm.menuOpen) return@LaunchedEffect
        kotlinx.coroutines.delay(5000)
        barHidden = true
    }

    CompositionLocalProvider(LocalHints provides hints) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onAnyTouch { lastTouch = System.currentTimeMillis() }) {
            AnimatedContent(
                vm.route to vm.stack.size,
                transitionSpec = {
                    val forward = targetState.second >= initialState.second
                    (slideInHorizontally(tween(240)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(240)) { if (forward) -it / 5 else it / 5 } + fadeOut(tween(180)))
                },
                contentKey = { it.first },
                label = "route",
            ) { (r, _) ->
                Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                    when (r) {
                        Route.Player -> AmbientHost(vm, vm.settings.value.ambient) { NowPlayingScreen(vm, bottom) }
                        Route.Library -> LibraryScreen(vm, bottom)
                        is Route.Detail -> DetailScreen(vm, r, bottom)
                        Route.Settings -> SettingsScreen(vm, bottom)
                        Route.Equalizer -> EqualizerScreen(vm, bottom)
                        Route.Stats -> StatsScreen(vm, bottom)
                        Route.Queue -> QueuePage(vm, bottom)
                        Route.Search -> SearchPage(vm, bottom)
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter)) {
                AnimatedVisibility(track != null && !onPlayer, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    MiniPlayer(vm)
                }
                AnimatedVisibility(!barHidden, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(tween(400))) {
                    BottomBar(vm)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = bottom))
            HintBubble(hints, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp))
        }
    }
    if (vm.menuOpen) MenuSheet(vm)
    DeleteHost(vm)
    UpdateDialog(vm)
}

/** Dolny pasek: biblioteka, korektor, szukaj, menu – płaskie ikony na czarnej pigułce. */
@Composable
private fun BottomBar(vm: MainViewModel) {
    val r = vm.route
    // Pasek na całą szerokość, wtopiony w dolną krawędź (tło sięga pod systemowy pasek nawigacji).
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().height(54.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val inLibrary = r == Route.Library || r is Route.Detail || r == Route.Stats || r == Route.Queue
        RoundButton(Icons.Rounded.GridView, tr("Biblioteka"), {
            if (r == Route.Library) vm.nowPlayingOpen = true else vm.goTab(Route.Library)
        }, active = inLibrary, background = Color.Transparent, size = 44.dp, iconSize = 24.dp)
        RoundButton(Icons.Rounded.Equalizer, tr("Korektor"), {
            if (r == Route.Equalizer) vm.nowPlayingOpen = true else vm.goTab(Route.Equalizer)
        }, active = r == Route.Equalizer, background = Color.Transparent, size = 44.dp, iconSize = 24.dp)
        RoundButton(Icons.Rounded.Search, tr("Szukaj"), {
            if (r != Route.Search) vm.goTab(Route.Search)
        }, active = r == Route.Search, background = Color.Transparent, size = 44.dp, iconSize = 24.dp)
        RoundButton(Icons.Rounded.Menu, tr("Menu"), { vm.menuOpen = true }, active = vm.menuOpen, background = Color.Transparent, size = 44.dp, iconSize = 24.dp)
    }
}

/** Menu (czwarty przycisk paska): skróty do wszystkich miejsc aplikacji. */
@Composable
private fun MenuSheet(vm: MainViewModel) {
    ModalBottomSheet(onDismissRequest = { vm.menuOpen = false }, containerColor = MaterialTheme.colorScheme.background, dragHandle = null) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            val go: (() -> Unit) -> () -> Unit = { a -> { vm.menuOpen = false; a() } }
            TileGrid(
                listOf(
                    Tile(Icons.Rounded.PlayCircle, tr("Odtwarzacz"), tr("Teraz odtwarzane"), vm.route == Route.Player) { go { vm.nowPlayingOpen = true }() },
                    Tile(Icons.AutoMirrored.Rounded.QueueMusic, tr("Kolejka"), tr("Kolejka"), false) { go { vm.nowPlayingOpen = true; vm.queueOpen = true }() },
                    Tile(Icons.Rounded.BarChart, tr("Statystyki"), tr("Statystyki"), vm.route == Route.Stats) { go { vm.goTab(Route.Stats) }() },
                    Tile(Icons.Rounded.Settings, tr("Ustawienia"), tr("Ustawienia"), vm.route == Route.Settings) { go { vm.goTab(Route.Settings) }() },
                ),
            )
            Gap(6.dp)
        }
    }
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
