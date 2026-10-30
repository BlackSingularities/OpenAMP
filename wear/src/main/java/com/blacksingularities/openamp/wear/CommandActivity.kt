package com.blacksingularities.openamp.wear

import android.app.Activity
import android.os.Bundle

/**
 * Niewidoczne okno: wysyła jedno polecenie do telefonu i zamyka się.
 * Potrzebne kafelkowi, bo kafelek może tylko uruchomić okno albo usługę.
 */
class CommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getStringExtra(EXTRA_ACTION)?.let { action ->
            WearPhone.send(applicationContext, action)
        }
        WearDataService.refreshFaces(applicationContext)
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "action"
    }
}
