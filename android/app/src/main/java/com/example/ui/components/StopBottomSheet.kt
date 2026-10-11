package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.ArrivalSource
import com.example.data.arrivalPhrase
import com.example.data.arrivalSourceLabel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RouteSchedule
import com.example.data.RouteSummary
import com.example.data.Stop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopBottomSheet(
    stop: Stop,
    routes: List<RouteSummary>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    arrivals: Map<String, Int?> = emptyMap(),
    /** Маршруты с прогнозом по живым GPS-данным; для остальных — по расписанию. */
    liveArrivalRouteIds: Set<String> = emptySet(),
    accessibleRouteIds: Set<String> = emptySet(),
    scheduleByRouteId: Map<String, RouteSchedule> = emptyMap(),
    dataDate: String = "",
    alarmStopId: String? = null,
    alarmVoice: Boolean = true,
    onSetAlarmVoice: (Boolean) -> Unit = {},
    onArmAlarm: () -> Unit = {},
    onCancelAlarm: () -> Unit = {},
    onTestAlarm: () -> Unit = {},
    onRouteClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSummary by remember { mutableStateOf<RouteSummary?>(null) }
    val isArmedHere = alarmStopId == stop.id

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Остановка общественного транспорта",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FavoriteToggleButton(
                    isFavorite = isFavorite,
                    itemLabel = "остановку «${stop.name}»",
                    onToggle = onToggleFavorite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Два оповещения: при подъезде (~350 м) и непосредственно у остановки.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isArmedHere) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = "Будильник к выходу",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Предупредит дважды: примерно за 350 м и у самой остановки. Работает с выключенным экраном и без интернета.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Голос (озвучить)", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(checked = alarmVoice, onCheckedChange = onSetAlarmVoice)
                    }
                    // «Проверить» — проиграть сигнал и голос прямо сейчас, чтобы услышать, как будет
                    TextButton(onClick = onTestAlarm) {
                        Text(if (alarmVoice) "▶ Проверить сигнал и голос" else "▶ Проверить сигнал")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isArmedHere) {
                        OutlinedButton(
                            onClick = onCancelAlarm,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Отключить оповещение") }
                    } else {
                        Button(
                            onClick = onArmAlarm,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("🔔 Разбудить у этой остановки") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedSummary == null) {
                Text(
                    text = "Маршруты через эту остановку:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(routes) { summary ->
                        RouteSummaryItem(
                            summary,
                            arrivals[summary.id],
                            arrivalSource = if (summary.id in liveArrivalRouteIds) ArrivalSource.LIVE_GPS else ArrivalSource.SCHEDULE,
                            isAccessible = summary.id in accessibleRouteIds
                        ) {
                            selectedSummary = summary
                        }
                    }
                }
            } else {
                ScheduleDetails(
                    summary = selectedSummary!!,
                    schedule = scheduleByRouteId[selectedSummary!!.id],
                    dataDate = dataDate,
                    etaMinutes = arrivals[selectedSummary!!.id],
                    arrivalSource = if (selectedSummary!!.id in liveArrivalRouteIds) ArrivalSource.LIVE_GPS else ArrivalSource.SCHEDULE
                ) {
                    selectedSummary = null
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onRouteClick(selectedSummary!!.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Показать маршрут на карте")
                }
            }
        }
    }
}

@Composable
fun RouteSummaryItem(
    summary: RouteSummary,
    etaMinutes: Int? = null,
    arrivalSource: ArrivalSource = ArrivalSource.SCHEDULE,
    isAccessible: Boolean = false,
    onClick: () -> Unit
) {
    // Названия остановок не склоняем: «→ куда» и «Отправление: откуда» читаются
    // правильно в именительном падеже (раньше было «до Административно-деловой центр»).
    val etaText = etaMinutes?.let(::arrivalPhrase)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Расписание маршрута", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Маршрут ${summary.number}, направление ${summary.destination}")
                    if (etaText != null) append(", ближайший $etaText ${arrivalSourceLabel(arrivalSource)}")
                    if (isAccessible) append(", низкопольный автобус")
                }
            }
            .padding(vertical = 8.dp)
    ) {
        RouteNumberBadge(number = summary.number, colorArgb = summary.color)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Стрелка отдельно от названия: длинное название переносится по словам,
            // а не посреди слова и не оставляет «→» одну на строке.
            Row {
                Text(text = "→ ", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = com.example.data.breakableAfterHyphens(summary.destination),
                    // «Административно-деловой» переносится по дефису,
                    // а не посреди слова без знака переноса.
                    style = MaterialTheme.typography.bodyLarge.copy(
                        hyphens = Hyphens.None,
                        lineBreak = LineBreak.Simple
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Отправление: ${summary.origin}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // Значок появляется только когда перевозчик реально передал
                // признак низкопольности по этому автобусу.
                if (isAccessible) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Accessible,
                        contentDescription = "Низкопольный автобус",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        // Прогноз прибытия: «через ~N мин» и откуда он взят
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = etaText ?: "нет рейсов",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (etaMinutes != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (etaMinutes != null) {
                Text(
                    text = arrivalSourceLabel(arrivalSource),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScheduleDetails(
    summary: RouteSummary,
    schedule: RouteSchedule?,
    dataDate: String,
    etaMinutes: Int?,
    arrivalSource: ArrivalSource = ArrivalSource.SCHEDULE,
    onBack: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("← Назад")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Маршрут №${summary.number}", style = MaterialTheme.typography.titleMedium)
        }

        // Ближайший рейс (ориентировочно, по расписанию или GPS)
        if (etaMinutes != null) {
            Text(
                text = "Ближайший рейс ${arrivalPhrase(etaMinutes)} (${arrivalSourceLabel(arrivalSource)})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ScheduleView(schedule = schedule, dataDate = dataDate)
            }
        }
    }
}
