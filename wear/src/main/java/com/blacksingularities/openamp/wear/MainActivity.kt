package com.blacksingularities.openamp.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText

/** Pilot OpenAMP na zegarku: ekran odtwarzania i kolejka. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }

    override fun onResume() {
        super.onResume()
        WearPhone.start(this)
    }

    override fun onPause() {
        super.onPause()
        WearPhone.stop(this)
    }
}

@Composable
private fun WearApp() {
    val state by WearPhone.state.collectAsStateWithLifecycle()
    val art by WearPhone.art.collectAsStateWithLifecycle()
    var queueOpen by remember { mutableStateOf(false) }
    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            if (queueOpen) {
                QueueScreen(state) { queueOpen = false }
            } else {
                PlayerScreen(state, art) { queueOpen = true }
            }
        }
    }
}
