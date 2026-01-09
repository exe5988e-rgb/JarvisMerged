package com.jarvismini.ui

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvismini.api.JarvisApiClient
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import com.jarvismini.core.WorkModeManager
import kotlinx.coroutines.launch

@Composable
fun JarvisChatScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // 🔧 ADDED: initialize JarvisState once
    LaunchedEffect(Unit) {
        JarvisState.init(context)
    }

    // 🔧 ADDED: request required permissions once
    LaunchedEffect(Unit) {
        if (activity == null) return@LaunchedEffect

        val perms = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) perms += android.Manifest.permission.READ_CONTACTS

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) perms += android.Manifest.permission.SEND_SMS

        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                perms.toTypedArray(),
                2001
            )
        }
    }

    // ===== UI STATE =====
    var currentMode by remember {
        mutableStateOf(JarvisState.currentMode)
    }

    var expanded by remember { mutableStateOf(false) }
    val modes = JarvisMode.values().toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Current mode: $currentMode",
            style = MaterialTheme.typography.titleMedium
        )

        // ===== Mode selector =====
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = currentMode.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Mode") },
                modifier = Modifier.menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                modes.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.name) },
                        onClick = {
                            JarvisState.setMode(context, mode)
                            currentMode = mode
                            expanded = false
                        }
                    )
                }
            }
        }

        Button(onClick = {
            WorkModeManager.toggle(context)
            currentMode = JarvisState.currentMode
        }) {
            Text("Toggle Work Mode")
        }

        Divider()

        AppButton("Open Physics Wallah", "xyz.penpencil.physicswala", context)
        AppButton("Open Wavelet", "com.pittvandewitt.wavelet", context)
        AppButton("Open YouTube Music", "com.google.android.apps.youtube.music", context)
        AppButton("Open Clock", "com.oneplus.deskclock", context)

        Divider()

        Button(onClick = {
            requestCallScreeningRole(context)
        }) {
            Text("Enable Call Auto-Reply")
        }

        Divider()

        Button(onClick = {
            scope.launch {
                try {
                    val reply = JarvisApiClient.getResponse("Hello Jarvis")
                    Toast.makeText(context, reply, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "API Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }) {
            Text("Ask Jarvis (Termux)")
        }
    }
}

@Composable
private fun AppButton(label: String, pkg: String, context: Context) {
    Button(onClick = {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(context, "App not installed", Toast.LENGTH_SHORT).show()
        } else {
            context.startActivity(intent)
        }
    }) {
        Text(label)
    }
}

private fun requestCallScreeningRole(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

    val roleManager = context.getSystemService(RoleManager::class.java)

    if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
        && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    ) {
        context.startActivity(
            roleManager.createRequestRoleIntent(
                RoleManager.ROLE_CALL_SCREENING
            )
        )
    } else {
        Toast.makeText(
            context,
            "Call screening already enabled",
            Toast.LENGTH_SHORT
        ).show()
    }
}
