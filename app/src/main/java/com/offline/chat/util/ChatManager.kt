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
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val line = reader.readLine()
                    val senderIp = client.inetAddress.hostAddress ?: "Bilinmiyor"

                    if (senderIp != localIp && !line.isNullOrEmpty()) {
                        val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        onMessageReceived(
                            ChatMessage(senderIp, line, timestamp, false)
                        )
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
            val socket = Socket(targetIp, port)
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            writer.write(message)
            writer.newLine()
            writer.flush()
            socket.close()
            true
        } catch (e: Exception) {
            onError("$targetIp'ye gönderilemedi: ${e.message}")
            false
        }
    }

    suspend fun findServer() = withContext(Dispatchers.IO) {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress
                        if (ip.startsWith("192.168.")) {
                            val prefix = ip.substringBeforeLast(".")

                            val searchJobs = (1..254).map { i ->
                                async(Dispatchers.IO) {
                                    val testIp = "$prefix.$i"
                                    try {
                                        val socket = Socket()
                                        socket.connect(InetSocketAddress(testIp, port), 200)
                                        socket.close()
                                        testIp
                                    } catch (_: IOException) {
                                        null
                                    }
                                }
                            }

                            for (job in searchJobs) {
                                val result = job.await()
                                if (result != null) {
                                    return@withContext result
                                }
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

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
    }
}