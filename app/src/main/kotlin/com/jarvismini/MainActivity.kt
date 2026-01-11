package com.jarvismini

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.jarvismini.core.JarvisState
import com.jarvismini.engine.EngineProvider
import com.jarvismini.ui.JarvisChatScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔴 CRITICAL FIX: init core BEFORE any UI or services rely on it
        JarvisState.init(applicationContext)
        EngineProvider.init(applicationContext)

        setContent {
            MaterialTheme {
                Surface {
                    JarvisChatScreen()
                }
            }
        }
    }
}
