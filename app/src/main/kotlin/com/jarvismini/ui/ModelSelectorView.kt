package com.jarvismini.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import com.jarvismini.R
import com.jarvismini.engine.LlamaLLMEngine
import com.jarvismini.engine.ModelStatus
import kotlinx.coroutines.*

/**
 * ✅ NEW: Model selector UI component
 * Allows users to choose between Auto, Chat, and Code models
 */
class ModelSelectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val tvStatus: TextView
    private val spinnerModelSelect: Spinner
    private val layoutLoading: View
    private val progressLoading: ProgressBar
    private val tvLoadingMessage: TextView
    private val tvModelInfo: TextView

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var statusCheckJob: Job? = null

    private val modelOptions = mutableListOf<ModelOption>()
    private var adapter: ModelAdapter? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_model_selector, this, true)

        tvStatus = findViewById(R.id.tvModelStatus)
        spinnerModelSelect = findViewById(R.id.spinnerModelSelect)
        layoutLoading = findViewById(R.id.layoutLoading)
        progressLoading = findViewById(R.id.progressModelLoading)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)
        tvModelInfo = findViewById(R.id.tvModelInfo)

        // Accessibility: announce status changes politely, name the spinner for TalkBack.
        tvStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        if (spinnerModelSelect.contentDescription.isNullOrEmpty()) {
            spinnerModelSelect.contentDescription = "AI model selection"
        }
        // Spinner and its row should be at least 48dp tall.
        spinnerModelSelect.minimumHeight = (48 * resources.displayMetrics.density).toInt()

        setupSpinner()
        startStatusMonitoring()
    }

    /** Set text/colour only when they changed: the 1 s poll must not re-announce an unchanged status. */
    private fun setStatus(text: String, color: Int) {
        if (tvStatus.text.toString() != text) tvStatus.text = text
        if (tvStatus.currentTextColor != color) tvStatus.setTextColor(color)
    }

    private fun setupSpinner() {
        adapter = ModelAdapter(context, modelOptions)
        spinnerModelSelect.adapter = adapter

        spinnerModelSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = modelOptions.getOrNull(position) ?: return
                LlamaLLMEngine.selectedModel = selected.id
                updateModelInfo(selected)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun startStatusMonitoring() {
        statusCheckJob = scope.launch {
            while (isActive) {
                updateStatus()
                delay(1000)  // Check every second
            }
        }
    }

    private suspend fun updateStatus() {
        val status = withContext(Dispatchers.IO) {
            LlamaLLMEngine.getModelStatus()
        }

        updateUI(status)
    }

    private fun updateUI(status: ModelStatus) {
        // Update loading indicator
        if (status.isLoading) {
            layoutLoading.visibility = View.VISIBLE
            setStatus("Loading...", context.getColor(android.R.color.holo_orange_light))
            spinnerModelSelect.isEnabled = false
        } else {
            layoutLoading.visibility = View.GONE
            spinnerModelSelect.isEnabled = true

            val readyCount = listOf(status.chatReady, status.codeReady).count { it }
            // Status is always text as well as colour.
            setStatus(
                when {
                    readyCount == 0 -> "No models"
                    readyCount == 1 -> "1 model ready"
                    else -> "$readyCount models ready"
                },
                if (readyCount > 0) context.getColor(android.R.color.holo_green_light)
                else context.getColor(android.R.color.holo_red_light)
            )
        }

        // Update available models
        updateAvailableModels(status)
    }

    private fun updateAvailableModels(status: ModelStatus) {
        val newOptions = mutableListOf<ModelOption>()

        // Always add Auto option
        newOptions.add(ModelOption("auto", "Auto", "Smart selection based on prompt"))

        // Add available models
        if (status.chatReady) {
            newOptions.add(ModelOption("chat", "Chat (Phi-2)", "General conversation & questions"))
        }
        if (status.codeReady) {
            newOptions.add(ModelOption("code", "Code (DeepSeek)", "Programming & code generation"))
        }

        // Only update if changed
        if (newOptions != modelOptions) {
            modelOptions.clear()
            modelOptions.addAll(newOptions)
            adapter?.notifyDataSetChanged()

            // Restore selection if possible
            val currentSelection = LlamaLLMEngine.selectedModel
            val index = modelOptions.indexOfFirst { it.id == currentSelection }
            if (index >= 0) {
                spinnerModelSelect.setSelection(index)
            }
        }
    }

    private fun updateModelInfo(option: ModelOption) {
        tvModelInfo.text = option.description
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        statusCheckJob?.cancel()
        scope.cancel()
    }

    private data class ModelOption(
        val id: String,
        val name: String,
        val description: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ModelOption) return false
            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()
    }

    private class ModelAdapter(
        context: Context,
        private val options: List<ModelOption>
    ) : ArrayAdapter<ModelOption>(context, android.R.layout.simple_spinner_item, options) {

        init {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            (view as? TextView)?.text = options[position].name
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent)
            (view as? TextView)?.text = options[position].name
            return view
        }
    }
}
