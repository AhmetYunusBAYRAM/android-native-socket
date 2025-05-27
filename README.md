# 📡 Offline Rescue Communication (Android)

Enable peer-to-peer communication between Android devices using local Wi-Fi — without internet or a central server.

This project allows real-time **text and voice message exchange** between a victim (server) and a rescuer (client) using LAN sockets.

---

## 🔧 Features

- 📶 No internet or mobile network required
- 📱 Peer-to-peer messaging over local Wi-Fi
- 📝 Real-time text messaging
- 🎙️ Voice message recording and playback
- 🔋 Battery level & device info sharing
- 📍 Approximate distance estimation using Wi-Fi signal strength
- 🔔 Notifications with vibration and sound

---

## 📱 Technology Stack

- Kotlin & Jetpack Compose
- Android Socket API (TCP)
- MediaRecorder / MediaPlayer for audio
- WiFiManager for signal info
- Compose UI + LazyColumn for chat interface

---

## 🛠️ Usage

1. One device acts as **Server (Victim)** — waits for connections
2. Another device acts as **Client (Rescuer)** — scans local network
3. Devices exchange messages directly once connected

> This app is designed for emergency environments such as earthquakes, tunnels, or remote areas where traditional communication is unavailable.

---

## ⚠️ Note

- Both devices must be connected to the **same local Wi-Fi network**
- Microphone permissions must be granted for audio recording

---

## 🧑‍💻 Author

Developed by Ahmet Yunus BAYRAM — for emergency communication and offline resilience.

