package com.jarvismini.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.jarvismini.api.JarvisApiClient

@Composable
fun JarvisChatScreen() {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {

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

        Row {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Jarvis…") }
            )

            Button(
                onClick = {
                    val userText = input
                    input = ""
                    messages.add(ChatMessage(userText, true))

                    scope.launch {
                        try {
                            val reply = JarvisApiClient.getResponse(userText)
                            messages.add(ChatMessage(reply, false))
                        } catch (e: Exception) {
                            messages.add(ChatMessage("Error: ${e.message}", false))
                        }
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}
