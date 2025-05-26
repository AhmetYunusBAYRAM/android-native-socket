package com.offline.chat
import android.os.Bundle
import android.os.BatteryManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*



data class ChatMessage(val sender: String, val text: String, val timestamp: String, val isLocal: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private fun getBatteryPercentage(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    private val port = 5050

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var role by remember { mutableStateOf<String?>(null) }

                when (role) {
                    null -> RoleSelectionScreen { selected -> role = selected }
                    "sender" -> SenderUI()
                    "server" -> ServerUI()
                }
            }
        }
    }

    @Composable
    fun RoleSelectionScreen(onRoleSelected: (String) -> Unit) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(title = { Text("Rol Seçimi", fontSize = 20.sp, fontWeight = FontWeight.Bold) })
            },
            containerColor = Color(0xFFF2F2F2)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { onRoleSelected("server") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Depremzede (Sunucu ve Mesaj Gönderici)")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onRoleSelected("sender") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kurtarıcı (Mesaj Gönder)")
                }
            }
        }
    }

    @Composable
    fun ServerUI() {
        val messages = remember { mutableStateListOf<ChatMessage>() }
        val scope = rememberCoroutineScope()
        val ipAddress = remember { mutableStateOf("Bulunuyor...") }
        var message by remember { mutableStateOf("") }
        var showDialog by remember { mutableStateOf(false) }

        // IP Adresi bulma
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

        // Sunucu dinleme
        LaunchedEffect(Unit) {
            scope.launch(Dispatchers.IO) {
                try {
                    val serverSocket = ServerSocket(port)
                    while (true) {
                        val client = serverSocket.accept()
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            line?.let {
                                val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                if (client.inetAddress.hostAddress != ipAddress.value) {
                                    messages.add(ChatMessage(client.inetAddress.hostAddress, it, timestamp, false))
                                }
                            }
                        }
                        client.close()
                    }
                } catch (e: Exception) {
                    val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    messages.add(ChatMessage("HATA", e.message ?: "Bilinmeyen", timestamp, false))
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Depremzede") },
                    actions = {
                        // Durum Gönder
                        IconButton(onClick = {
                            val batteryStatus = getBatteryPercentage()
                            val deviceModel = android.os.Build.MODEL ?: "Bilinmiyor"
                            val manufacturer = android.os.Build.MANUFACTURER ?: "Bilinmiyor"
                            val sdkVersion = android.os.Build.VERSION.SDK_INT
                            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                            val wifiName = wifiManager.connectionInfo.ssid ?: "Bilinmiyor"

                            val infoMessage = """ 📱 Batarya: $batteryStatus%, 📱 Cihaz: $manufacturer $deviceModel, 📦 Android SDK: $sdkVersion, 📶 Ağ: $wifiName """.trimIndent()

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val socket = Socket("127.0.0.1", port)
                                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                                    writer.write(infoMessage)
                                    writer.flush()
                                    socket.close()
                                } catch (_: Exception) {}
                            }
                        }) {
                            Icon(imageVector = Icons.Filled.Info, contentDescription = "Durum Gönder")
                        }

                        // Ayarlar
                        IconButton(onClick = { showDialog = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
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
                        .padding(8.dp)
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
                        scope.launch(Dispatchers.IO) {
                            try {
                                val socket = Socket("127.0.0.1", port)
                                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                                writer.write(message)
                                writer.newLine()
                                writer.flush()
                                socket.close()
                                message = ""
                            } catch (_: Exception) {}
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

    @Composable
    fun SenderUI() {
        val port = 5050
        val messages = remember { mutableStateListOf<ChatMessage>() }
        val scope = rememberCoroutineScope()
        var discoveredIp by remember { mutableStateOf("Sunucu aranıyor...") }
        var message by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            scope.launch(Dispatchers.IO) {
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    for (intf in interfaces) {
                        val addresses = intf.inetAddresses
                        for (addr in addresses) {
                            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                                val ip = addr.hostAddress
                                if (ip.startsWith("192.168.")) {
                                    val prefix = ip.substringBeforeLast(".")
                                    for (i in 1..254) {
                                        val testIp = "$prefix.$i"
                                        try {
                                            val socket = Socket()
                                            socket.connect(InetSocketAddress(testIp, port), 200)
                                            socket.close()
                                            discoveredIp = testIp
                                            break
                                        } catch (_: IOException) {}
                                    }
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    discoveredIp = "IP bulunamadı"
                }
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(title = { Text("Kurtarıcı (Mesaj Gönder)", fontSize = 20.sp, fontWeight = FontWeight.Bold) })
            },
            containerColor = Color(0xFFF2F2F2)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🌐 Sunucu IP: $discoveredIp", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Mesajınızı yazın") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val socket = Socket(discoveredIp, port)
                            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                            writer.write(message)
                            writer.newLine()
                            writer.flush()
                            socket.close()
                            val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            messages.add(ChatMessage("Ben", message, timestamp, true))
                            message = ""
                        } catch (e: Exception) {
                            val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            messages.add(ChatMessage("HATA", e.message ?: "Gönderme hatası", timestamp, true))
                        }
                    }
                }) {
                    Text("Gönder")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("🔔 Sohbet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
            }
        }
    }
}