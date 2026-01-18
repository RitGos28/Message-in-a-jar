package com.ritwikg.messageinajar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ritwikg.messageinajar.ui.theme.MessageInAJarTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.delay
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.Instant

@Serializable
data class JarMessage(
    val createdAt: Long,
    val tag: String,
    val content: String
)


// 1. Create a DataStore instance (defined at top level so it's a singleton)
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // 1️⃣ Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        // 2️⃣ Create notification channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "jar_channel",
                "Jar Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when the jar unlocks"
            }


            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        enableEdgeToEdge()

        setContent {
            MessageInAJarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    JarScreen(
                        modifier = Modifier.padding(innerPadding),
                        onJarUnlocked = {}
                    )
                }
            }
        }



    }

}

fun notifyIfPermitted(
    context: Context,
    notificationId: Int,
    notification: android.app.Notification
) {
    // Android 13+ needs explicit permission
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            // Permission denied → do nothing gracefully
            return
        }
    }

    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

fun today(): LocalDate =
    LocalDate.now(ZoneId.systemDefault())

fun unlockDateForYear(year: Int): LocalDate =
    LocalDate.of(year, 12, 21)

fun isJarUnlocked(today: LocalDate): Boolean {
    val unlock = unlockDateForYear(today.year)
    val lockAgain = LocalDate.of(today.year + 1, 1, 1)

    return !today.isBefore(unlock) && today.isBefore(lockAgain)
}

fun daysUntilUnlock(today: LocalDate): Long {
    val unlock = unlockDateForYear(today.year)

    return if (today.isBefore(unlock))
        ChronoUnit.DAYS.between(today, unlock)
    else
        0
}

fun sendJarNotification(context: Context) {
    // Android 13+ runtime permission check
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
    }

    val notification = NotificationCompat.Builder(context, "jar_channel")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("The jar is ready 🫙")
        .setContentText("Your message can now be revealed")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(1, notification)
}

@Composable
fun JarScreen(
    modifier: Modifier = Modifier,
    onJarUnlocked: () -> Unit
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messageInput by remember { mutableStateOf("") }

    val MESSAGES_KEY = stringPreferencesKey("jar_messages")


    val messages by context.dataStore.data
        .map { prefs ->
            val json = prefs[MESSAGES_KEY] ?: "[]"
            Json.decodeFromString<List<JarMessage>>(json)
        }
        .collectAsState(initial = emptyList())



    var uiNow by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val today = remember(uiNow) {
        Instant.ofEpochMilli(uiNow)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
    val unlocked = isJarUnlocked(today)
    val daysLeft = daysUntilUnlock(today)

    LaunchedEffect(Unit) {
        while (true) {
            uiNow = System.currentTimeMillis()
            delay(1000)
        }
    }



    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.jar),
            contentDescription = "Jar",
            modifier = Modifier.size(200.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextField(
            value = messageInput,
            onValueChange = { messageInput = it },
            placeholder = { Text("Leave a message for future you") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!unlocked) {
            Button(
                onClick = {
                    try{
                        // 4. Save to DataStore inside a Coroutine
                        scope.launch {
                            val seconds = 0L // UI removed, notification logic kept
                            val unlockTime = System.currentTimeMillis() + (seconds * 1000)

                            context.dataStore.edit { prefs ->
                                val existing = prefs[MESSAGES_KEY]?.let {
                                    Json.decodeFromString<List<JarMessage>>(it)
                                } ?: emptyList()

                                val newMessage = JarMessage(
                                    createdAt = System.currentTimeMillis(),
                                    tag = "default",
                                    content = messageInput
                                )

                                val updated = existing + newMessage
                                prefs[MESSAGES_KEY] = Json.encodeToString(updated)
                            }

                            // 3️⃣ UX feedback
                            Toast.makeText(context, "Message sealed in the jar!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Alarm setup failed", Toast.LENGTH_LONG).show()
                    }

                }
            ) {
                Text("Seal the jar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (!unlocked) {
            Text("Jar unlocks in $daysLeft days")
        } else {
            Text("The jar is open 🫙")
        }

    }
}
