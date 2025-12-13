package com.jarvismini.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.jarvismini.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Minimal activity: App will not crash
        // Add UI or buttons later if needed
    }
}
