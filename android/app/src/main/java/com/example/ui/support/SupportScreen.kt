package com.example.ui.support

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.map.MapViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var includeDiagnostics by remember { mutableStateOf(false) }
    val supportAvailable = remember {
        runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull()
            ?.let { it.isNotBlank() && !it.contains("placeholder", ignoreCase = true) } == true
    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Поддержка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sanyaragora24@gmail.com"))
                        .putExtra(Intent.EXTRA_SUBJECT, "Норильский транспорт: поддержка")
                        .putExtra(Intent.EXTRA_TEXT, messageText + if (includeDiagnostics) {
                            "\n\n" + getDiagnosticsString(context, uiState.selectedRoute?.id, uiState.selectedStop?.id)
                        } else "")
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Support email", "sanyaragora24@gmail.com"))
                        scope.launch { snackbarHostState.showSnackbar("Почтовое приложение не найдено. Адрес скопирован.") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Написать по электронной почте")
            }

            if (!supportAvailable) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Отправка через приложение пока не подключена. Напишите сообщение ниже и отправьте его по электронной почте.")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Contact Form
            Text(
                text = "Отправить сообщение разработчикам",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it.take(MAX_SUPPORT_MESSAGE_LENGTH) },
                label = { Text("Ваше сообщение") },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                enabled = !isSending
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isSending) { includeDiagnostics = !includeDiagnostics }
                    .padding(vertical = 8.dp)
            ) {
                Checkbox(
                    checked = includeDiagnostics,
                    onCheckedChange = { includeDiagnostics = it },
                    enabled = !isSending
                )
                Text("Приложить диагностические данные", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (messageText.isBlank() || !supportAvailable) return@Button
                    isSending = true
                    
                    val ticket = mutableMapOf<String, Any>(
                        "createdAt" to Date(),
                        "message" to messageText,
                        "appVersion" to BuildConfig.VERSION_NAME,
                        "deviceModel" to Build.MODEL,
                        "androidVersion" to Build.VERSION.RELEASE,
                        "userLocale" to Locale.getDefault().toString()
                    )
                    
                    if (includeDiagnostics) {
                        ticket["diagnostics"] = getDiagnosticsString(context, uiState.selectedRoute?.id, uiState.selectedStop?.id)
                    }
                    fun submitTicket() {
                        FirebaseFirestore.getInstance().collection("support_messages")
                            .add(ticket)
                            .addOnSuccessListener {
                            isSending = false
                            messageText = ""
                            scope.launch {
                                snackbarHostState.showSnackbar("Сообщение отправлено!")
                            }
                            }
                            .addOnFailureListener {
                            isSending = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Ошибка отправки")
                            }
                            }
                    }

                    val auth = FirebaseAuth.getInstance()
                    if (auth.currentUser != null) {
                        submitTicket()
                    } else {
                        auth.signInAnonymously()
                            .addOnSuccessListener { submitTicket() }
                            .addOnFailureListener {
                                isSending = false
                                scope.launch { snackbarHostState.showSnackbar("Ошибка авторизации") }
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = supportAvailable && !isSending && messageText.isNotBlank()
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Отправить")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Diagnostics Copy
            TextButton(
                onClick = {
                    val data = getDiagnosticsString(context, uiState.selectedRoute?.id, uiState.selectedStop?.id)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Diagnostics", data)
                    clipboard.setPrimaryClip(clip)
                    scope.launch {
                        snackbarHostState.showSnackbar("Скопировано в буфер")
                    }
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Копировать диагностику")
            }
        }
    }
}

fun getDiagnosticsString(context: Context, routeId: String?, stopId: String?): String {
    return """
        App: Norilsk Transport ${BuildConfig.VERSION_NAME}
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Android: ${Build.VERSION.RELEASE}
        Locale: ${Locale.getDefault()}
        Selected Route: ${routeId ?: "none"}
        Selected Stop: ${stopId ?: "none"}
    """.trimIndent()
}

private const val MAX_SUPPORT_MESSAGE_LENGTH = 2_000
