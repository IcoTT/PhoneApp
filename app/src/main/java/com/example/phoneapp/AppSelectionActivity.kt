package com.example.phoneapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.phoneapp.ui.theme.PhoneAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

// Common social media apps
val SOCIAL_MEDIA_PACKAGES = setOf(
    // Meta
    "com.facebook.katana",          // Facebook
    "com.facebook.lite",            // Facebook Lite
    "com.facebook.orca",            // Messenger
    "com.facebook.mlite",           // Messenger Lite
    "com.instagram.android",        // Instagram
    "com.whatsapp",                 // WhatsApp

    // Other social
    "com.twitter.android",          // Twitter/X
    "com.x.android",                // X (new package)
    "com.zhiliaoapp.musically",     // TikTok
    "com.ss.android.ugc.trill",     // TikTok (alternative)
    "com.snapchat.android",         // Snapchat
    "com.linkedin.android",         // LinkedIn
    "com.pinterest",                // Pinterest
    "com.tumblr",                   // Tumblr
    "com.reddit.frontpage",         // Reddit

    // Messaging
    "org.telegram.messenger",       // Telegram
    "com.discord",                  // Discord
    "com.viber.voip",               // Viber
    "com.tencent.mm",               // WeChat
    "jp.naver.line.android",        // LINE
    "com.skype.raider",             // Skype

    // Video/Streaming
    "com.google.android.youtube",   // YouTube
    "com.vimeo.android.videoapp",   // Vimeo
    "tv.twitch.android.app",        // Twitch

    // Dating
    "com.tinder",                   // Tinder
    "com.bumble.app",               // Bumble

    // Other
    "com.spotify.music",            // Spotify
    "com.netflix.mediaclient",      // Netflix
    "com.zhiliaoapp.musically"      // TikTok
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

    // Search query
    var searchQuery by remember { mutableStateOf("") }

    // Show all apps toggle
    var showAllApps by remember { mutableStateOf(false) }

    // Loading state
    var isLoading by remember { mutableStateOf(true) }
    var socialApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    // Load apps in background
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val installed = getInstalledApps(context)
            val social = installed.filter { SOCIAL_MEDIA_PACKAGES.contains(it.packageName) }

            withContext(Dispatchers.Main) {
                allApps = installed
                socialApps = social
                isLoading = false
            }
        }
    }

    // Choose which list to show
    val displayApps = if (showAllApps) allApps else socialApps

    // Filter apps based on search
    val filteredApps = remember(searchQuery, displayApps) {
        if (searchQuery.isEmpty()) {
            displayApps
        } else {
            displayApps.filter { app ->
                app.appName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

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

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear"
                        )
                    }
                }
            },
            singleLine = true,
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Toggle for showing all apps
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (showAllApps)
                    "${filteredApps.size} apps"
                else
                    "${filteredApps.size} social apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show all apps",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = showAllApps,
                    onCheckedChange = { showAllApps = it },
                    enabled = !isLoading
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading apps...")
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showAllApps)
                        "No apps found"
                    else
                        "No social media apps found.\nTry enabling 'Show all apps'",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(filteredApps) { app ->
                    AppListItem(
                        app = app,
                        isSelected = selectedApps.contains(app.packageName),
                        onToggle = { isSelected ->
                            selectedApps = if (isSelected) {
                                selectedApps + app.packageName
                            } else {
                                selectedApps - app.packageName
                            }
                        }
                    )
                    HorizontalDivider()
                }
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
                    prefs.edit()
                        .putStringSet("monitored_apps", selectedApps)
                        .apply()
                    onDone()
                },
                modifier = Modifier.width(120.dp),
                enabled = !isLoading
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current

    // Load icon lazily
    var icon by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(app.packageName) {
        withContext(Dispatchers.IO) {
            val loadedIcon = loadAppIcon(context, app.packageName)
            withContext(Dispatchers.Main) {
                icon = loadedIcon
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isSelected) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onToggle
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon!!.asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Text(
                    text = app.appName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager

    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val launcherApps = pm.queryIntentActivities(intent, 0)

    return launcherApps
        .filter { resolveInfo ->
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

private fun loadAppIcon(context: Context, packageName: String): Bitmap? {
    return try {
        val pm = context.packageManager
        val drawable = pm.getApplicationIcon(packageName)
        drawableToBitmap(drawable)
    } catch (e: Exception) {
        null
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }

    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)

    return bitmap
}
