package ru.myski.vodokanal.smsgateway

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import ru.myski.vodokanal.smsgateway.data.GatewayConfig
import ru.myski.vodokanal.smsgateway.service.SmsGatewayService
import ru.myski.vodokanal.smsgateway.ui.theme.SMSGatewayTheme
import java.net.Inet4Address

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SMSGatewayTheme {
                DashboardScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val config = remember { GatewayConfig(context) }
    var isRunning by remember { mutableStateOf(SmsGatewayService.isRunning) }
    var ipAddress by remember { mutableStateOf(getLocalIpAddress(context)) }
    val apiKey = remember { config.apiKey }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.SEND_SMS)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startGatewayService(context)
            isRunning = SmsGatewayService.isRunning
        } else {
            Toast.makeText(context, "Permissions required to start gateway", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("SMS Gateway") },
                actions = {
                    IconButton(onClick = { ipAddress = getLocalIpAddress(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh IP")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val displayValue = if (ipAddress.startsWith("192.") || ipAddress.startsWith("10.") || ipAddress.startsWith("172.")) {
                "http://$ipAddress:8082"
            } else {
                ipAddress
            }
            InfoCard(
                title = "Local IP Address",
                value = displayValue,
            ) {
                if (displayValue.startsWith("http")) {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Gateway IP", displayValue)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "IP Address copied", Toast.LENGTH_SHORT).show()
                }
            }

            ApiKeyCard(apiKey = apiKey)

            StatusCard(isRunning = isRunning)

            Button(
                onClick = {
                    if (isRunning) {
                        stopGatewayService(context)
                        isRunning = false
                    } else {
                        if (hasPermissions(context, permissionsToRequest)) {
                            startGatewayService(context)
                            isRunning = true
                        } else {
                            launcher.launch(permissionsToRequest)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isRunning) "Stop Gateway" else "Start Gateway")
            }
        }
    }
    
    // Refresh status periodically
    LaunchedEffect(Unit) {
        while(true) {
            isRunning = SmsGatewayService.isRunning
            delay(1.seconds)
        }
    }
}

@Composable
fun InfoCard(title: String, value: String, onCopy: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            onCopy?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }
        }
    }
}

@Composable
fun ApiKeyCard(apiKey: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "API Key", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = apiKey,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("API Key", apiKey)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "API Key copied", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }
        }
    }
}

@Composable
fun StatusCard(isRunning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Status", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (isRunning) "Running" else "Stopped",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun startGatewayService(context: Context) {
    val intent = Intent(context, SmsGatewayService::class.java)
    context.startForegroundService(intent)
}

private fun stopGatewayService(context: Context) {
    val intent = Intent(context, SmsGatewayService::class.java)
    context.stopService(intent)
}

private fun getLocalIpAddress(context: Context): String {
    try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "Wi-Fi не подключён"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Wi-Fi не подключён"
        
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "Wi-Fi не подключён"
        }
        
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return "Wi-Fi не подключён"
        
        for (linkAddress in linkProperties.linkAddresses) {
            val address = linkAddress.address
            if (address is Inet4Address && !address.isLoopbackAddress) {
                if (address.isSiteLocalAddress) {
                    return address.hostAddress ?: continue
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "Wi-Fi не подключён"
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    SMSGatewayTheme {
        DashboardScreen()
    }
}
