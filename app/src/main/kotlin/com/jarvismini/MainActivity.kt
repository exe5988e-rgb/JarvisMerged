//===== FILE: app/src/main/kotlin/com/jarvismini/MainActivity.kt =====
package com.jarvismini

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.jarvismini.ui.JarvisChatScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface {
                    JarvisChatScreen()
                }
            }
        }
    }
}
