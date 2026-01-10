package com.jarvismini.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.jarvismini.api.JarvisApiClient

// ✅ DATA MODEL (MISSING BEFORE)
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

@Composable
fun JarvisChatScreen() {

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {

        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                Text(
                    text = if (msg.isUser) "You: ${msg.text}" else "Jarvis: ${msg.text}",
                    modifier = Modifier.padding(6.dp)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {

            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Jarvis…") }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    val userText = input.trim()
                    if (userText.isEmpty()) return@Button

                    input = ""
                    messages.add(ChatMessage(userText, true))

                    scope.launch {
                        try {
                            // ✅ CORRECT API CALL
                            JarvisApiClient.sendQuery(userText)

                            val reply = JarvisApiClient.getResponse()
                            messages.add(ChatMessage(reply, false))

                        } catch (e: Exception) {
                            messages.add(
                                ChatMessage(
                                    "Error: ${e.message}",
                                    false
                                )
                            )
                        }
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}
