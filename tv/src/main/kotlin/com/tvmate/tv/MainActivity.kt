package com.tvmate.tv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tvmate.shared.ui.TvMateTheme

class MainActivity : ComponentActivity() {
    private lateinit var host: TvHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = (application as TvApp).host
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Keys sent from the phone remote are replayed here so the TV UI reacts like a real remote.
        host.keyInjector = { code ->
            runOnUiThread {
                dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
        }
        setContent {
            TvMateTheme {
                val screen by host.screen.collectAsState()
                // Back leaves the player first; on Home the system handles it (exits the app).
                BackHandler(enabled = screen == TvScreen.Player) { host.leavePlayer() }
                TvRoot(host)
            }
        }
    }

    override fun onDestroy() {
        host.keyInjector = null
        super.onDestroy()
    }
}
