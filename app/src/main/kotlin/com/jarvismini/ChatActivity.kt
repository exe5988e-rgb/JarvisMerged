package com.jarvismini

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jarvismini.engine.LlamaLLMEngine
import com.jarvismini.ui.ModelSelectorView
import kotlinx.coroutines.launch

/**
 * ✅ UPDATED: Chat activity with model selector integration
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var modelSelector: ModelSelectorView

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initViews()
        setupRecyclerView()
        setupListeners()
    }

    private fun initViews() {
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        modelSelector = findViewById(R.id.modelSelector)
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(messages)
        rvMessages.adapter = adapter
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
    }

    private fun setupListeners() {
        btnSend.setOnClickListener {
            sendMessage()
        }

        etMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        // ✅ NEW: Check if models are still loading
        if (LlamaLLMEngine.isLoading) {
            Toast.makeText(
                this,
                "⏳ Models are still loading... Please wait a moment.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Add user message
        val userMessage = ChatMessage(text, isUser = true)
        messages.add(userMessage)
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)

        // Clear input
        etMessage.text.clear()

        // Disable input while processing
        etMessage.isEnabled = false
        btnSend.isEnabled = false
        etMessage.hint = "Processing..."

        // Add "thinking" indicator
        val thinkingMessage = ChatMessage("Thinking...", isUser = false, isThinking = true)
        messages.add(thinkingMessage)
        val thinkingIndex = messages.size - 1
        adapter.notifyItemInserted(thinkingIndex)
        rvMessages.scrollToPosition(thinkingIndex)

        // Generate reply
        lifecycleScope.launch {
            try {
                val reply = LlamaLLMEngine.generateReply(text)

                // Remove thinking indicator
                messages.removeAt(thinkingIndex)
                adapter.notifyItemRemoved(thinkingIndex)

                // Add actual reply
                val replyMessage = ChatMessage(reply, isUser = false)
                messages.add(replyMessage)
                adapter.notifyItemInserted(messages.size - 1)
                rvMessages.scrollToPosition(messages.size - 1)

            } catch (e: Exception) {
                // Remove thinking indicator
                messages.removeAt(thinkingIndex)
                adapter.notifyItemRemoved(thinkingIndex)

                // Show error
                val errorMessage = ChatMessage(
                    "❌ Error: ${e.message}",
                    isUser = false
                )
                messages.add(errorMessage)
                adapter.notifyItemInserted(messages.size - 1)
                rvMessages.scrollToPosition(messages.size - 1)

            } finally {
                // Re-enable input
                etMessage.isEnabled = true
                btnSend.isEnabled = true
                etMessage.hint = "Type a message..."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release resources if this is the last activity
        if (isFinishing) {
            // Note: Only release if app is actually closing
            // LlamaLLMEngine.release()
        }
    }
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isThinking: Boolean = false
)
