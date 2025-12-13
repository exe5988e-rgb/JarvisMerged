package com.jarvismini.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.jarvismini.engine.LLMManager
import com.jarvismini.R

class SettingsActivity : AppCompatActivity() {

    private val modelList = listOf(
        "gpt-4o-mini",
        "gpt-4o",
        "gpt-4.1",
        "gpt-5.1",
        "gpt-5.2",
        "o3",
        "gpt-image"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val spinner = findViewById<Spinner>(R.id.modelSpinner)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            modelList
        )
        spinner.adapter = adapter

        // FIXED: Spinner listeners cannot be lambdas
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                LLMManager.updateModel(this@SettingsActivity, modelList[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No action needed
            }
        }
    }
}
