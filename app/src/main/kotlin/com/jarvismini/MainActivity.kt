package com.jarvismini

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var simulateButton: Button
    private lateinit var modeSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Status text
        statusText = TextView(this).apply {
            textSize = 16f
            text = "Current mode: ${JarvisState.currentMode}"
            setPadding(24, 24, 24, 24)
        }

        // Spinner
        modeSpinner = Spinner(this)

        val modes = JarvisMode.values().map { it.name }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            modes
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = adapter

        modeSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedMode = JarvisMode.valueOf(modes[position])
                    JarvisState.currentMode = selectedMode
                    statusText.text = "Current mode: $selectedMode"
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        // Button
        simulateButton = Button(this).apply {
            text = "Simulate Incoming Message"
            setOnClickListener { simulateIncomingMessage() }
        }

        // Layout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusText)
            addView(modeSpinner)
            addView(simulateButton)
        }

        setContentView(layout)
    }

    private fun simulateIncomingMessage() {
        val input = AutoReplyInput(
            messageText = "Hello Jarvis! Are you there?",
            isFromOwner = false
        )

        val decision = AutoReplyOrchestrator.handle(input)

        statusText.text = when (decision) {
            is ReplyDecision.AutoReply ->
                "AutoReply: ${decision.message}"

            ReplyDecision.NoReply ->
                "No reply decision made"
        }
    }
}
