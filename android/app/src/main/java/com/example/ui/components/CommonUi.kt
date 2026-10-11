package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Route
import com.example.data.RouteSchedule
import com.example.data.ScheduleEstimator
import com.example.data.Stop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteCard(
    route: Route,
    onBadgeClick: () -> Unit,
    onTextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            // Number Badge: нажатие сразу показывает маршрут на карте
            RouteNumberBadge(
                number = route.number,
                colorArgb = route.color,
                size = 56.dp,
                fontSize = 22.sp,
                cornerRadius = 12.dp,
                borderWidth = 2.5.dp,
                modifier = Modifier
                    .clickable(
                        onClickLabel = "Показать на карте",
                        role = Role.Button,
                        onClick = onBadgeClick
                    )
                    .semantics { contentDescription = "Маршрут ${route.number}, показать на карте" }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Info text: одна область нажатия на всю карточку направления
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        onClickLabel = "Остановки и расписание",
                        role = Role.Button,
                        onClick = onTextClick
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Маршрут ${route.number}: ${route.origin} → ${route.destination}"
                    }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = route.origin,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Направление — подпись, а не отдельная кнопка-чип 24 dp
                // (раньше это была третья область нажатия в одной строке).
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = if (route.origin == route.destination) "↻ кольцевой" else "→\u00A0${route.destination}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StopCard(
    stop: Stop,
    distance: String? = null,
    /**
     * Номера маршрутов через эту остановку. В Норильске у большинства остановок
     * есть пара на встречном направлении с тем же названием — без маршрутов две
     * одинаковые строки в списке выглядят как ошибка приложения.
     */
    routeNumbers: List<String> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(stop.name, fontWeight = FontWeight.Bold) },
        supportingContent = {
            Text(
                text = if (routeNumbers.isEmpty()) {
                    "Остановка общественного транспорта"
                } else {
                    "Маршруты: " + routeNumbers.joinToString(", ")
                },
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = { 
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) 
        },
        trailingContent = distance?.let { 
            { 
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                ) 
            }
        }
    )
}

@Composable
fun ScheduleChips(times: List<String>, modifier: Modifier = Modifier) {
    if (times.isEmpty()) {
        Column(modifier = modifier) {
            Text(
                text = "Интервал 10-20 мин",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Точные времена будут добавлены при подключении источника данных",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(times) { time ->
                SuggestionChip(
                    onClick = { },
                    label = { Text(time) }
                )
            }
        }
    }
}

/**
 * Полное расписание маршрута по часам (офлайн, официальные данные НПОПАТ).
 * Переключатель Будни/Выходные + времена, сгруппированные по часам, по каждой конечной.
 */
@Composable
fun ScheduleView(
    schedule: RouteSchedule?,
    dataDate: String,
    modifier: Modifier = Modifier
) {
    if (schedule == null || !schedule.hasSchedule || schedule.timetable.isEmpty()) {
        Column(modifier = modifier) {
            Text(
                text = "Расписание уточняется",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Для этого маршрута перевозчик пока не опубликовал расписание.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var weekend by remember(schedule.routeId) { mutableStateOf(ScheduleEstimator.isWeekend()) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !weekend, onClick = { weekend = false }, label = { Text("Будни") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = weekend, onClick = { weekend = true }, label = { Text("Выходные") })
        }

        schedule.timetable.forEach { tt ->
            val times = if (weekend) tt.weekend else tt.weekday
            Text(
                text = "Отправление от: ${tt.terminal}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
            if (times.isEmpty()) {
                Text(
                    text = "нет рейсов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HourlyTimetable(times)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Данные от $dataDate · сверяйтесь на norilsk-bus.ru",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Времена, сгруппированные по часам: «07 | 20  55» и т.д. */
@Composable
private fun HourlyTimetable(times: List<String>) {
    val byHour = LinkedHashMap<String, MutableList<String>>()
    for (t in times) {
        val parts = t.split(":")
        if (parts.size != 2) continue
        val h = parts[0].padStart(2, '0')
        byHour.getOrPut(h) { mutableListOf() }.add(parts[1])
    }
    Column {
        byHour.forEach { (hour, mins) ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 24.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = hour,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = mins.joinToString("   "),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Бейдж с номером маршрута в цвете перевозчика. Цвет цифр подбирается по
 * контрасту: на жёлтых бейджах (15, 31, 31Э) белые цифры не читались.
 */
@Composable
fun RouteNumberBadge(
    number: String,
    colorArgb: Long,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    fontSize: TextUnit = 18.sp,
    cornerRadius: Dp = 10.dp,
    borderWidth: Dp = 2.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    val textColor = Color(RouteColors.badgeTextColor(colorArgb))
    Box(
        modifier = modifier
            .size(size)
            .background(Color(colorArgb), shape)
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, Color(0xFF111111), shape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            color = textColor,
            fontWeight = FontWeight.Black,
            fontSize = fontSize,
            maxLines = 1,
            style = if (RouteColors.badgeNeedsShadow(colorArgb)) {
                androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 3f)
                )
            } else {
                androidx.compose.ui.text.TextStyle.Default
            }
        )
    }
}

/**
 * Звёздочка «Избранное» с состоянием для TalkBack: озвучивается, что именно
 * добавляется и добавлено ли оно уже.
 */
@Composable
fun FavoriteToggleButton(
    isFavorite: Boolean,
    itemLabel: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.semantics {
            stateDescription = if (isFavorite) "В избранном" else "Не в избранном"
        }
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (isFavorite) "Убрать $itemLabel из избранного" else "Добавить $itemLabel в избранное",
            tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
