package com.offline.chat.screens

import android.content.Context
import android.media.RingtoneManager
import android.os.Vibrator
import android.os.VibrationEffect
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offline.chat.model.ChatMessage
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import com.offline.chat.util.ChatManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderUI() {
    val port = 5050
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    var discoveredIp by remember { mutableStateOf("Sunucu aranıyor...") }
    var isSearching by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    fun triggerNotification() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
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

    LaunchedEffect(messages.size) {
        listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
    }

    LaunchedEffect(Unit) {
        scope.launch { chatManager.startServer() }
    }

    // Sunucuyu ara ve bağlan
    LaunchedEffect(Unit) {
        scope.launch {
            val serverIp = chatManager.findServer()
            if (serverIp != null) {
                discoveredIp = serverIp
                val ipMessage = "📡 Bağlandı: $serverIp"
                chatManager.sendMessage(serverIp, ipMessage)
                val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                messages.add(ChatMessage("Ben", ipMessage, timestamp, true))
            } else {
                discoveredIp = "Sunucu bulunamadı"
            }
            isSearching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                    val wifiName = wifiManager?.connectionInfo?.ssid ?: "Bilinmiyor"

                    Column {
                        Text("Kurtarıcı", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (isSearching) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Sunucu aranıyor...", fontSize = 12.sp, color = Color.Gray)
                            }
                        } else {
                            Text("Sunucu IP: $discoveredIp", fontSize = 12.sp, color = Color.Gray)
                            Text("WiFi: $wifiName", fontSize = 12.sp, color = Color.Gray)
                        }
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
                        ) {
                            Text(msg.sender, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text(msg.text, fontSize = 14.sp)
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
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Mesaj yaz...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (message.isNotBlank() && discoveredIp != "Sunucu bulunamadı" && !isSearching) {
                        scope.launch {
                            if (chatManager.sendMessage(discoveredIp, message)) {
                                val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                messages.add(ChatMessage("Ben", message, timestamp, true))
                                message = ""
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