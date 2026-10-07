package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.network.WeatherAlert
import com.example.data.network.WeatherAlertLevel

/**
 * Карточка штормового предупреждения над картой.
 *
 * Свёрнутая по умолчанию: заголовок и одна строка сути. По нажатию
 * раскрывается совет — что делать пассажиру. Закрыть можно крестиком,
 * но предупреждение вернётся, если погода ухудшится ещё сильнее.
 */
@Composable
fun WeatherAlertBanner(
    alert: WeatherAlert,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(alert.id) { mutableStateOf(false) }

    val containerColor = when (alert.level) {
        WeatherAlertLevel.SEVERE -> MaterialTheme.colorScheme.errorContainer
        WeatherAlertLevel.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        WeatherAlertLevel.ADVISORY -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (alert.level) {
        WeatherAlertLevel.SEVERE -> MaterialTheme.colorScheme.onErrorContainer
        WeatherAlertLevel.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        WeatherAlertLevel.ADVISORY -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = if (alert.level == WeatherAlertLevel.ADVISORY) Icons.Default.Info else Icons.Default.Warning

    // Для TalkBack озвучиваем предупреждение целиком: незрячий пассажир —
    // одна из ключевых аудиторий приложения.
    val spokenDescription = "${levelWord(alert.level)}. ${alert.title}. ${alert.message} ${alert.advice}"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = spokenDescription }
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = alert.advice,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.9f)
                        )
                    }
                }
                if (!expanded) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Нажмите, чтобы узнать, что делать",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрыть предупреждение",
                    tint = contentColor
                )
            }
        }
    }
}

private fun levelWord(level: WeatherAlertLevel): String = when (level) {
    WeatherAlertLevel.SEVERE -> "Опасное явление"
    WeatherAlertLevel.WARNING -> "Предупреждение"
    WeatherAlertLevel.ADVISORY -> "Обратите внимание"
}
