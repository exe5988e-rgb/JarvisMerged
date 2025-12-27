package com.jarvismini

import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var modeSpinner: Spinner
    private lateinit var enableCallButton: Button

    private val PERM_REQ = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        JarvisState.init(this)
        requestPermissions()

        statusText = TextView(this).apply {
            text = "Current mode: ${JarvisState.currentMode}"
            textSize = 16f
        }

        modeSpinner = Spinner(this)
        val modes = JarvisMode.values().map { it.name }
        modeSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)

        modeSpinner.setSelection(JarvisMode.values().indexOf(JarvisState.currentMode))

        modeSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    pos: Int,
                    id: Long
                ) {
                    val mode = JarvisMode.valueOf(modes[pos])
                    JarvisState.setMode(this@MainActivity, mode)
                    statusText.text = "Current mode: $mode"
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        enableCallButton = Button(this).apply {
            text = "Enable Call Auto-Reply"
            setOnClickListener { requestCallScreeningRole() }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusText)
            addView(modeSpinner)
            addView(enableCallButton)
        }

        setContentView(layout)
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val roleManager = getSystemService(RoleManager::class.java)

        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
            && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        ) {
            startActivity(
                roleManager.createRequestRoleIntent(
                    RoleManager.ROLE_CALL_SCREENING
                )
            )
        } else {
            Toast.makeText(this, "Call screening already enabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) perms += android.Manifest.permission.READ_CONTACTS

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) perms += android.Manifest.permission.SEND_SMS

        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERM_REQ)
        }
    }
}
