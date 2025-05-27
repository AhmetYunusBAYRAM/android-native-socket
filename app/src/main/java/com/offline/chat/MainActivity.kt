@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.offline.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import com.offline.chat.screens.SenderUI
import com.offline.chat.screens.ServerUI


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestPermissionLauncher =  registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (!isGranted) {
                    // Kullanıcı reddettiyse yapılacaklar
                }
            }

        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        setContent {
            MaterialTheme {
                val roleState = remember { mutableStateOf<String?>(null) }
                var role by roleState

                when (role) {
                    null -> RoleSelectionScreen { selected -> role = selected }
                    "sender" -> SenderUI()
                    "server" -> ServerUI()
                }
            }
        }
    }
}

@Composable
fun RoleSelectionScreen(onRoleSelected: (String) -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Rol Seçimi",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
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
