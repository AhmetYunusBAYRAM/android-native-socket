// ✅ ChatManager.kt (Geliştirilmiş)
// - AUDIO: başlığı ile gelen sesli mesajları tanır
// - BufferedInputStream ile veri ayrımı yapılır
// - Metin ve sesli mesajlar ayırt edilir
// - Time-out, exception kontrolü ve bağlantı kapatma güvenli hale getirilmiştir

package com.offline.chat.util

import com.offline.chat.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*

class ChatManager(
    private val port: Int = 5050,
    private val onMessageReceived: (ChatMessage) -> Unit,
    private val onError: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    private fun getLocalIpAddress(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            val addresses = intf.inetAddresses
            for (addr in addresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress
                }
            }
        }
        return null
    }

    suspend fun startServer() = withContext(Dispatchers.IO) {
        val localIp = getLocalIpAddress()
        try {
            serverSocket = ServerSocket(port)
            isRunning = true

            while (isRunning) {
                try {
                    val client = serverSocket?.accept() ?: break
                    val senderIp = client.inetAddress.hostAddress ?: "Bilinmiyor"

                    if (senderIp != localIp) {
                        val input = client.getInputStream()
                        val bufferedInput = BufferedInputStream(input)
                        bufferedInput.mark(10)

                        val header = ByteArray(6)
                        bufferedInput.read(header)
                        bufferedInput.reset()

                        val headerString = String(header)
                        val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                        if (headerString.startsWith("AUDIO:")) {
                            bufferedInput.skip(6)
                            val objectInput = ObjectInputStream(bufferedInput)
                            val audioBytes = objectInput.readObject() as? ByteArray
                            if (audioBytes != null) {
                                onMessageReceived(ChatMessage(senderIp, "[Sesli Mesaj]", timestamp, false, isAudio = true, audioBytes = audioBytes))
                            }
                        } else {
                            val reader = BufferedReader(InputStreamReader(bufferedInput))
                            val text = reader.readLine()
                            if (!text.isNullOrEmpty()) {
                                onMessageReceived(ChatMessage(senderIp, text, timestamp, false))
                            }
                        }
                    }
                    client.close()
                } catch (e: Exception) {
                    onError("Bağlantı hatası: ${e.message}")
                }
            }
        } catch (e: Exception) {
            onError("Sunucu başlatılamadı: ${e.message}")
        } finally {
            stopServer()
        }
    }

    suspend fun sendMessage(targetIp: String, message: String) = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(targetIp, port), 3000)
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            writer.write(message)
            writer.newLine()
            writer.flush()
            socket.close()
            true
        } catch (e: Exception) {
            onError("$targetIp'ye mesaj gönderilemedi: ${e.message}")
            false
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
    }

    suspend fun findServer(): String? = withContext(Dispatchers.IO) {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress
                        if (ip.startsWith("192.168.")) {
                            val prefix = ip.substringBeforeLast(".")
                            val jobs = (1..254).map { i ->
                                async(Dispatchers.IO) {
                                    val testIp = "$prefix.$i"
                                    try {
                                        val socket = Socket()
                                        socket.connect(InetSocketAddress(testIp, port), 300)
                                        socket.close()
                                        testIp
                                    } catch (_: IOException) {
                                        null
                                    }
                                }
                            }
                            for (job in jobs) {
                                val found = job.await()
                                if (found != null) return@withContext found
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            onError("Sunucu arama hatası: ${e.message}")
            null
        }
    }
}