package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.WeatherUi
import com.example.data.network.RoadSeverity
import com.example.data.network.assessRoadConditions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WeatherOverlay(
    visible: Boolean,
    isLoading: Boolean,
    weather: WeatherUi?,
    onDismiss: () -> Unit,
    /** Причина сбоя первого запроса — показывается вместо пустого экрана. */
    errorMessage: String? = null,
    onRetry: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Погода в Норильске",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    }

                    if (isLoading && weather == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        Text("Загрузка данных...")
                    } else if (weather != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${weather.temp.toInt()}°C",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Ощущается как ${weather.feelsLike.toInt()}°C",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = weather.description.replaceFirstChar { it.uppercase() },
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Ветер: ${weather.windSpeed} м/с",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        
                        // Карточка «Дорожные условия» — считается прямо в телефоне из погоды
                        val road = assessRoadConditions(weather)
                        val roadColor = when (road.severity) {
                            RoadSeverity.DANGER -> Color(0xFFD32F2F)
                            RoadSeverity.CAUTION -> Color(0xFFF9A825)
                            RoadSeverity.NORMAL -> Color(0xFF388E3C)
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = roadColor.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(roadColor, RoundedCornerShape(5.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Дорожные условия", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(road.title, fontWeight = FontWeight.Bold, color = roadColor)
                                Spacer(Modifier.height(4.dp))
                                road.factors.forEach { f ->
                                    Text("• $f", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    road.recommendation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )

                        val timeStr = DateTimeFormatter.ofPattern("HH:mm")
                            .withZone(ZoneId.of("Asia/Krasnoyarsk"))
                            .format(Instant.ofEpochMilli(weather.updateTime))
                        
                        Text(
                            text = "Обновлено в $timeStr (по НПР)",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End),
                            color = MaterialTheme.colorScheme.outline
                        )
                        
                        if (isLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    } else {
                        // Первый запрос не удался: не пустой экран, а понятная причина и повтор.
                        Text(
                            text = errorMessage ?: "Погода временно недоступна",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Проверьте соединение. Данные берутся для Норильска (69.35, 88.20).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onRetry, enabled = !isLoading) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isLoading) "Обновляем…" else "Повторить")
                        }
                    }
                }
            }
        }
    }
}
