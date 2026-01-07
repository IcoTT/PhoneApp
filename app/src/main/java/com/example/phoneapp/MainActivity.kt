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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.LocalTextStyle

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
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        if (pendingAppSelection && hasUsageStatsPermission()) {
            pendingAppSelection = false
            startActivity(Intent(this, AppSelectionActivity::class.java))
        } else {
            updateUI()
        }
    }

    private fun updateUI() {
        setContent {
            PhoneAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        isMonitoring = isMonitoringEnabled(this),
                        onStart = { checkAllPermissionsAndStart() },
                        onStop = { stopTimerService() },
                        onChooseApps = { openAppSelection() },
                        onClose = { finish() },
                        onSettingsChanged = { restartServiceIfRunning() }
                    )
                }
            }
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
                "Please enable 'Display over other apps' for Social Detox, then come back and tap Start again",
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
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val monitoredApps = prefs.getStringSet("monitored_apps", emptySet()) ?: emptySet()
        val timeLimit = prefs.getInt("time_limit", 10)
        
        if (monitoredApps.isEmpty()) {
            Toast.makeText(this, "Please select at least one app to monitor", Toast.LENGTH_SHORT).show()
            return
        }

        if (timeLimit <= 0) {
            Toast.makeText(this, "Please set a valid time limit", Toast.LENGTH_SHORT).show()
            return
        }

        setMonitoringEnabled(this, true)
        val intent = Intent(this, TimerService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, "Monitoring started!", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun stopTimerService() {
        setMonitoringEnabled(this, false)
        val intent = Intent(this, TimerService::class.java)
        stopService(intent)
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun restartServiceIfRunning() {
        if (isMonitoringEnabled(this)) {
            stopService(Intent(this, TimerService::class.java))
            startForegroundService(Intent(this, TimerService::class.java))
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    isMonitoring: Boolean = false,
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
    onChooseApps: () -> Unit = {},
    onClose: () -> Unit = {},
    onSettingsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

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

        Spacer(modifier = Modifier.height(8.dp))

        if (isMonitoring) {
            Text(
                text = "● Monitoring active",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = "○ Not monitoring",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Time limit",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = minutes,
            onValueChange = { newValue ->
                // Allow only digits, max 2 characters
                val filtered = newValue.filter { it.isDigit() }.take(2)
                minutes = filtered
                val timeLimit = filtered.toIntOrNull() ?: 10
                prefs.edit().putInt("time_limit", timeLimit).apply()
                onSettingsChanged()
            },
            label = { Text("Minutes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(100.dp),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onChooseApps,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Choose Apps")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.width(120.dp)
            ) {
                Text("Close")
            }

            if (isMonitoring) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.width(120.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop")
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.width(120.dp)
                ) {
                    Text("Start")
                }
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