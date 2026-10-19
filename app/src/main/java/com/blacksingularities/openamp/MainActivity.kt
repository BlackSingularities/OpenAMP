package com.blacksingularities.openamp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.blacksingularities.openamp.ui.AppRoot
import com.blacksingularities.openamp.ui.MainViewModel
import com.blacksingularities.openamp.update.UpdateManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        UpdateManager.clearUpdatedNotification(this)
        handle(intent)
        setContent { AppRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) vm.nowPlayingOpen = true
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATE, false)) {
            vm.updateDialog = true
            vm.checkUpdate()
        }
        // Otwarcie pliku audio z innej aplikacji: jeśli jest w bibliotece – odtwarzamy go z niej.
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            lifecycleScope.launch {
                Graph.ensureScanned()
                vm.player.state.first { it.connected }
                val id = uri.lastPathSegment?.toLongOrNull()
                val all = Graph.library.tracks.value
                val track = id?.let { Graph.library.get(it) }
                    ?: all.firstOrNull { uri.path?.endsWith(it.path.substringAfterLast('/')) == true }
                if (track != null) {
                    vm.player.play(listOf(track))
                    vm.nowPlayingOpen = true
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
        const val EXTRA_OPEN_UPDATE = "open_update"
    }
}
