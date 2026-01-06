package com.example.phoneapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.phoneapp.ui.theme.PhoneAppTheme

class MainActivity : ComponentActivity() {

    private var pendingAppSelection = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkOverlayAndStart()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhoneAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        onSaveAndStart = { checkAllPermissionsAndStart() },
                        onChooseApps = { openAppSelection() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If we were waiting for usage stats permission, check and proceed
        if (pendingAppSelection && hasUsageStatsPermission()) {
            pendingAppSelection = false
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }
    }

    private fun openAppSelection() {
        if (!hasUsageStatsPermission()) {
            pendingAppSelection = true
            Toast.makeText(
                this,
                "Please enable Usage Access for Social Detox",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun checkAllPermissionsAndStart() {
        // First check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkOverlayAndStart()
    }

    private fun checkOverlayAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Please enable 'Display over other apps' for Social Detox, then come back and tap Save again",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            startTimerService()
        }
    }

    private fun startTimerService() {
        val intent = Intent(this, TimerService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, "Monitoring started!", Toast.LENGTH_SHORT).show()
        finish()
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSaveAndStart: () -> Unit = {},
    onChooseApps: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // Load saved value (default 10 minutes)
    var minutes by remember { mutableStateOf(prefs.getInt("time_limit", 10).toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Social Detox",
            fontSize = 28.sp,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Set your time limit for social media",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = minutes,
            onValueChange = {
                minutes = it.filter { char -> char.isDigit() }
            },
            label = { Text("Minutes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(150.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Choose Apps button
        OutlinedButton(
            onClick = onChooseApps,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text(
                "Choose Apps"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    (context as? ComponentActivity)?.finish()
                },
                modifier = Modifier.width(120.dp)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    val timeLimit = minutes.toIntOrNull() ?: 10
                    prefs.edit().putInt("time_limit", timeLimit).apply()
                    onSaveAndStart()
                },
                modifier = Modifier.width(120.dp)
            ) {
                Text("Save")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    PhoneAppTheme {
        SettingsScreen()
    }
}