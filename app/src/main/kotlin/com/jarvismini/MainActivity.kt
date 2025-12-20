package com.jarvismini

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.automation.decision.NoReplyDecision

/**
 * MainActivity now allows:
 * - Monitoring automation decisions
 * - Simulating incoming messages for testing
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var simulateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple UI setup
        statusText = TextView(this).apply {
            textSize = 16f
            text = "Jarvis status will appear here"
            setPadding(24, 24, 24, 24)
        }

        simulateButton = Button(this).apply {
            text = "Simulate Incoming Message"
            setOnClickListener { simulateIncomingMessage() }
        }

        // Linear layout to hold text and button
        val layout = androidx.appcompat.widget.LinearLayoutCompat(this).apply {
            orientation = androidx.appcompat.widget.LinearLayoutCompat.VERTICAL
            addView(statusText)
            addView(simulateButton)
        }

        setContentView(layout)
    }

    /**
     * Simulate a new incoming message and run orchestrator
     */
    private fun simulateIncomingMessage() {
        val testMessage = "Hello Jarvis! Are you awake?"

        val input = AutoReplyInput(
            messageText = testMessage,
            isFromOwner = false
        )

        val decision = AutoReplyOrchestrator.handle(input)

        // Display the result
        when (decision) {
    is ReplyDecision.AutoReply ->
        statusText.text = "AutoReply: ${decision.message}"

    ReplyDecision.NoReply ->
        statusText.text = "No reply decision made"
        }
    }
}
