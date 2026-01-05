package com.example.phoneapp

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.phoneapp.ui.theme.PhoneAppTheme
import android.content.Intent

class AppSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhoneAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppSelectionScreen(
                        modifier = Modifier.padding(innerPadding),
                        onDone = { finish() }
                    )
                }
            }
        }
    }
}

data class AppInfo(
    val packageName: String,
    val appName: String
)

@Composable
fun AppSelectionScreen(
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // Load saved selected apps
    val savedApps = prefs.getStringSet("monitored_apps", emptySet()) ?: emptySet()
    var selectedApps by remember { mutableStateOf(savedApps.toSet()) }

    // Get installed apps
    val installedApps = remember { getInstalledApps(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Choose Apps to Monitor",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select the apps you want to limit:",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(installedApps) { app ->
                val isSelected = selectedApps.contains(app.packageName)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedApps = if (isSelected) {
                                selectedApps - app.packageName
                            } else {
                                selectedApps + app.packageName
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            selectedApps = if (checked) {
                                selectedApps + app.packageName
                            } else {
                                selectedApps - app.packageName
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                HorizontalDivider()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.width(120.dp)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    // Save selected apps
                    prefs.edit()
                        .putStringSet("monitored_apps", selectedApps)
                        .apply()
                    onDone()
                },
                modifier = Modifier.width(120.dp)
            ) {
                Text("Save")
            }
        }
    }
}

private fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager

    // Get all apps that have a launcher icon (apps user can actually open)
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val launcherApps = pm.queryIntentActivities(intent, 0)

    return launcherApps
        .filter { resolveInfo ->
            // Exclude our own app
            resolveInfo.activityInfo.packageName != context.packageName
        }
        .map { resolveInfo ->
            AppInfo(
                packageName = resolveInfo.activityInfo.packageName,
                appName = resolveInfo.loadLabel(pm).toString()
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.appName.lowercase() }
}