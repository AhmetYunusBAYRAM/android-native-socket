package com.offline.chat.screens

import android.content.Context
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
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
import com.offline.chat.util.getBatteryPercentage
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import com.offline.chat.util.ChatManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerUI() {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    val ipAddress = remember { mutableStateOf("Bulunuyor...") }
    var message by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    fun triggerNotification() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }

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
        scope.launch(Dispatchers.IO) {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                for (intf in interfaces) {
                    val addresses = intf.inetAddresses
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            ipAddress.value = addr.hostAddress
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                ipAddress.value = "IP bulunamadı"
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            chatManager.startServer()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Depremzede") },
                actions = {
                    IconButton(onClick = {
                        val batteryStatus = getBatteryPercentage(context)
                        val deviceModel = android.os.Build.MODEL ?: "Bilinmiyor"
                        val manufacturer = android.os.Build.MANUFACTURER ?: "Bilinmiyor"
                        val sdkVersion = android.os.Build.VERSION.SDK_INT
                        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                        val wifiName = wifiManager.connectionInfo.ssid ?: "Bilinmiyor"

                        val infoMessage = """
📱 Batarya: $batteryStatus%
📱 Cihaz: $manufacturer $deviceModel
📦 Android SDK: $sdkVersion
📶 Ağ: $wifiName
""".trimIndent()

                        val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        messages.add(ChatMessage("Ben", infoMessage, timestamp, true))

                        val activeIps = messages.map { it.sender }.toSet().filterNot { it == "HATA" || it == "Ben" || it == "Bilinmiyor" }

                        scope.launch(Dispatchers.IO) {
                            for (ip in activeIps) {
                                try {
                                    val socket = Socket(ip, 5050)
                                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                                    writer.write(infoMessage)
                                    writer.newLine()
                                    writer.flush()
                                    socket.close()
                                } catch (_: Exception) {}
                            }
                        }
                    }) {
                        Icon(imageVector = Icons.Filled.Info, contentDescription = "Durum Gönder")
                    }

                    IconButton(onClick = { showDialog = true }) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "Ayarlar")
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
                    .padding(8.dp),
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
                    if (message.isNotBlank()) {
                        scope.launch {
                            val activeIps = messages.map { it.sender }.toSet()
                                .filterNot { it == "HATA" || it == "Ben" || it == "Bilinmiyor" }

                            if (activeIps.isEmpty()) {
                                val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                messages.add(ChatMessage("HATA", "Bağlı cihaz bulunamadı", timestamp, true))
                                return@launch
                            }

                            var anySuccess = false
                            for (targetIp in activeIps) {
                                if (chatManager.sendMessage(targetIp, message)) {
                                    anySuccess = true
                                }
                            }

                            if (anySuccess) {
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

        if (showDialog) {
            val activeIps = messages.map { it.sender }.toSet().filterNot { it == "HATA" || it == "Ben" }
            AlertDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Kapat")
                    }
                },
                title = { Text("Ayarlar / Bilgiler") },
                text = {
                    Text(
                        """
📡 IP Adresi: ${ipAddress.value}
📶 Bağlı Cihazlar: ${activeIps.size}
🧑 IP'ler:
${activeIps.joinToString("\n")}
""".trimIndent()
                    )
                }
            )
        }
    }
}
