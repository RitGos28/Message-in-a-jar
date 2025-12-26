package com.ritwikg.messageinajar

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
import androidx.compose.ui.tooling.preview.Preview
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

// 1. Create a DataStore instance (defined at top level so it's a singleton)
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // 1️⃣ Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
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
    private fun showTestNotification() {
        val notification = NotificationCompat.Builder(this, "jar_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Jar unlocked 🫙")
            .setContentText("Your message is ready to be revealed.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        with(NotificationManagerCompat.from(this)) {
            notify(1, notification)
        }
    }

}


fun sendJarNotification(context: Context) {
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

    // 2. Define the key for our saved message
    val MESSAGE_KEY = stringPreferencesKey("saved_message")
    val UNLOCK_TIME_KEY = longPreferencesKey("unlock_time")


    // 3. Read the saved message from DataStore as a State
    val savedMessage by context.dataStore.data
        .map { preferences -> preferences[MESSAGE_KEY] ?: "" }
        .collectAsState(initial = "")
    val unlockTime by context.dataStore.data
        .map { prefs -> prefs[UNLOCK_TIME_KEY] ?: 0L }
        .collectAsState(initial = 0L)





    // UI State for the TextField
    var messageInput by remember { mutableStateOf("") }
    // UI-only state for a revealed (non-persistent) message
    var revealedMessage by remember { mutableStateOf<String?>(null) }
    var lockSeconds by remember { mutableStateOf("10") } // default
    var uiNow by remember { mutableStateOf(System.currentTimeMillis()) }

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

        TextField(
            value = lockSeconds,
            onValueChange = { lockSeconds = it.filter { c -> c.isDigit() } },
            label = { Text("Lock for (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(16.dp))

    if (savedMessage.isEmpty()) {
        Button(
            onClick = {
                try{
                    // 4. Save to DataStore inside a Coroutine
                    scope.launch {
                        val seconds = lockSeconds.toLongOrNull() ?: 0L
                        val unlockTime = System.currentTimeMillis() + (seconds * 1000)

                        // 1️⃣ Save message + unlock time
                        context.dataStore.edit { settings ->
                            settings[MESSAGE_KEY] = messageInput
                            settings[UNLOCK_TIME_KEY] = unlockTime
                        }

                        // 2️⃣ Schedule OS-level alarm (THIS IS THE NEW PART)
                        val alarmManager =
                            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                        val intent = Intent(context, JarAlarmReceiver::class.java)

                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            0,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            unlockTime,
                            pendingIntent
                        )

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

        val now = uiNow

        if (savedMessage.isNotEmpty() && now < unlockTime) {
            val remaining = (unlockTime - now) / 1000
            Text("Jar unlocks in $remaining seconds")
        }


        if (savedMessage.isNotEmpty() && now >= unlockTime) {


            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        // 1. Move message into memory (temporary)
                        revealedMessage = savedMessage

                        // 2. DELETE message from persistence
                        context.dataStore.edit { settings ->
                            settings.remove(MESSAGE_KEY)
                        }
                    }
                }
            ) {
                Text("REVEAL MESSAGE")
            }
        }

        revealedMessage?.let {

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Revealed message: $it",
                style = MaterialTheme.typography.bodyMedium
            )
        }

    }
}