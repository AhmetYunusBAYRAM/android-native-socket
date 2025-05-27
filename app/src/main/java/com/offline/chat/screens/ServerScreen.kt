// ✅ ServerScreen.kt - Geliştirilmiş
// - Gerçek batarya bilgisi gösterilir
// - Sesli mesaj "AUDIO:" başlığı ile gönderilir
// - Toast ile ses kaydı bildirimleri gösterilir

package com.offline.chat.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.BatteryManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.offline.chat.model.ChatMessage
import com.offline.chat.util.ChatManager
import kotlinx.coroutines.*
import java.io.File
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerUI() {
    val port = 5050
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val recorder = remember { MediaRecorder() }
    var isRecording by remember { mutableStateOf(false) }
    val outputFile = File(context.cacheDir, "server_audio.3gp")

    LaunchedEffect(Unit) {
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            0
        )
    }

    fun triggerNotification() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, notification).play()
    }

    val chatManager = remember {
        ChatManager(
            onMessageReceived = { msg ->
                messages.add(msg)
                triggerNotification()
            },
            onError = { error ->
                val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                messages.add(ChatMessage("HATA", error, timestamp, false))
            }
        )
    }

    LaunchedEffect(Unit) {
        scope.launch { chatManager.startServer() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Depremzede", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        val cihaz = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                        val sdk = android.os.Build.VERSION.SDK_INT
                        val mesaj = """
📱 Batarya: %$batteryLevel
📱 Cihaz: $cihaz
📦 Android SDK: $sdk
""".trimIndent()

                        val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        messages.add(ChatMessage("Ben", mesaj, timestamp, true))

                        val aktifIps = messages.map { it.sender }.distinct().filterNot { it == "Ben" || it == "HATA" }
                        scope.launch(Dispatchers.IO) {
                            for (ip in aktifIps) {
                                try {
                                    val socket = Socket(ip, port)
                                    val writer = socket.getOutputStream().bufferedWriter()
                                    writer.write(mesaj)
                                    writer.newLine()
                                    writer.flush()
                                    socket.close()
                                } catch (_: Exception) {}
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Info, contentDescription = "Bilgi Gönder")
                    }
                }
            )
        },
        containerColor = Color(0xFFF2F2F2)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                state = listState,
                reverseLayout = false
            ) {
                items(messages) { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = if (msg.isLocal) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    if (msg.isLocal) Color(0xFFD1E7DD) else Color.White,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(8.dp)
                                .widthIn(max = 300.dp)
                                .clickable(enabled = msg.isAudio && msg.audioBytes != null) {
                                    if (msg.isAudio && msg.audioBytes != null) {
                                        try {
                                            val tempFile = File.createTempFile("audio", ".3gp", context.cacheDir)
                                            tempFile.writeBytes(msg.audioBytes)
                                            val player = MediaPlayer()
                                            player.setDataSource(tempFile.absolutePath)
                                            player.prepare()
                                            player.start()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                        ) {
                            Text(msg.sender, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text(if (msg.isAudio) "[Sesli Mesaj]" else msg.text, fontSize = 14.sp)
                            Text(msg.timestamp, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (!isRecording) {
                        try {
                            Toast.makeText(context, "🎙️ Ses kaydı başladı", Toast.LENGTH_SHORT).show()
                            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                            recorder.setOutputFile(outputFile.absolutePath)
                            recorder.prepare()
                            recorder.start()
                            isRecording = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        try {
                            recorder.stop()
                            recorder.reset()
                            isRecording = false
                            val audioBytes = outputFile.readBytes()
                            val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            messages.add(ChatMessage("Ben", "[Sesli Mesaj]", timestamp, true, isAudio = true, audioBytes = audioBytes))

                            val aktifIps = messages.map { it.sender }.distinct().filterNot { it == "Ben" || it == "HATA" }
                            scope.launch(Dispatchers.IO) {
                                for (ip in aktifIps) {
                                    try {
                                        val socket = Socket(ip, port)
                                        val outputStream: OutputStream = socket.getOutputStream()
                                        outputStream.write("AUDIO:".toByteArray())
                                        val objectOutput = ObjectOutputStream(outputStream)
                                        objectOutput.writeObject(audioBytes)
                                        objectOutput.flush()
                                        socket.close()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            Toast.makeText(context, "✅ Sesli mesaj gönderildi", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Sesli Mesaj Gönder")
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Mesaj yaz...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (message.isNotBlank()) {
                        val aktifIps = messages.map { it.sender }.distinct().filterNot { it == "Ben" || it == "HATA" }
                        scope.launch {
                            for (ip in aktifIps) {
                                if (chatManager.sendMessage(ip, message)) {
                                    val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    messages.add(ChatMessage("Ben", message, timestamp, true))
                                    message = ""
                                }
                            }
                        }
                    }
                }) {
                    Text("Gönder")
                }
            }
        }
    }
}
