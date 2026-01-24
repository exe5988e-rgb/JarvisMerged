package com.jarvismini

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jarvismini.core.JarvisState
import com.jarvismini.engine.EngineProvider
import com.jarvismini.ui.JarvisChatScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Core init MUST stay before UI
        JarvisState.init(applicationContext)
        EngineProvider.init(applicationContext)

        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "chat"
                    ) {
                        composable("chat") {
                            MainNav()
                        }

                        // 🔜 Future screens plug in here
                        // composable("checklist") { DailyChecklistScreen() }
                        // composable("calendar") { DayCalendarScreen(...) }
                    }
                }
            }
        }
    }
}
