@file:OptIn(ExperimentalMaterial3Api::class)

package com.jarvismini.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.jarvismini.core.progress.*
import com.jarvismini.core.routine.model.Routine
import com.jarvismini.core.tts.AssistantTTS
import com.jarvismini.ui.components.GridBackground

private val JarvisBlue = Color(0xFF00E0FF)

@Composable
fun JarvisChecklistScreen(
    blocks: List<ProgressBlock>,
    routines: List<Routine>,
    onBlocksUpdated: (List<ProgressBlock>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val stats = ProgressStatsEngine.getTodayStats()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color.Black, Color(0xFF001520), Color.Black)
                )
            )
    ) {
        GridBackground()

        Column(modifier = Modifier.fillMaxSize()) {

            // ===== HEADER BAR =====
            TopAppBar(
                title = {
                    Text(
                        "J.A.R.V.I.S TASK MATRIX",
                        color = JarvisBlue,
                        fontFamily = FontFamily.Monospace
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = JarvisBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {

                Text(
                    text = "SYSTEM COMPLETION: ${stats.completionPercent}%",
                    fontSize = 12.sp,
                    color = JarvisBlue.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )

                Spacer(Modifier.height(16.dp))

                if (blocks.isEmpty()) {
                    Text(
                        text = "NO ACTIVE ROUTINES",
                        color = JarvisBlue,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(blocks) { block ->

                            val routine =
                                routines.find { it.id == block.id } ?: return@items

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        JarvisBlue.copy(alpha = 0.4f),
                                        RoundedCornerShape(10.dp)
                                    ),
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp)
                            ) {

                                ChecklistItem(
                                    block = block,
                                    routine = routine,

                                    // ✅ PROPER SCREEN → ENGINE WIRING
                                    onComplete = {
                                        ProgressEngine.markComplete(context, block.id)
                                        AssistantTTS.speak(context, "Task completed.")
                                        onBlocksUpdated(
                                            ProgressRepository.getTodayBlocks()
                                        )
                                    },

                                    onIncomplete = {
                                        ProgressEngine.markIncomplete(context, block.id)
                                        AssistantTTS.speak(context, "Task marked incomplete.")
                                        onBlocksUpdated(
                                            ProgressRepository.getTodayBlocks()
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
