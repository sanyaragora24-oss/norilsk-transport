package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Route
import com.example.data.Stop
import com.example.data.local.FavoriteGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSheet(
    routes: List<Route>,
    favoriteRouteIds: Set<String>,
    favoriteStopIds: Set<String>,
    groups: List<FavoriteGroup>,
    onDismiss: () -> Unit,
    onToggleFavoriteRoute: (String) -> Unit,
    onToggleFavoriteStop: (String) -> Unit,
    onSelectRoute: (Route) -> Unit,
    onSelectStop: (Stop) -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onToggleRouteInGroup: (String, String) -> Unit,
    onToggleStopInGroup: (String, String) -> Unit,
    savedJourneys: List<com.example.data.local.SavedJourney> = emptyList(),
    onOpenSavedJourney: (com.example.data.local.SavedJourney) -> Unit = {},
    onDeleteSavedJourney: (com.example.data.local.SavedJourney) -> Unit = {}
) {
    var selectedGroupId by remember { mutableStateOf<String?>(null) } // null = «Все»
    var addMode by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FavoriteGroup?>(null) }

    val allStops = remember(routes) { routes.flatMap { it.stops }.distinctBy { it.id } }
    val favRoutesAll = routes.filter { favoriteRouteIds.contains(it.id) }
    val favStopsAll = allStops.filter { favoriteStopIds.contains(it.id) }

    val selectedGroup = groups.find { it.id == selectedGroupId }
    val favRoutes = if (selectedGroup == null) favRoutesAll
        else favRoutesAll.filter { selectedGroup.routeIds.contains(it.id) }
    val favStops = if (selectedGroup == null) favStopsAll
        else favStopsAll.filter { selectedGroup.stopIds.contains(it.id) }

    ModalBottomSheet(
        onDismissRequest = { onDismiss(); addMode = false },
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Избранное",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (addMode) {
                    TextButton(onClick = { addMode = false }) { Text("Готово") }
                } else {
                    FilledTonalButton(onClick = { addMode = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Маршрут")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Чипы групп: Все | <группы> | ＋ Группа ---
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedGroupId == null,
                        onClick = { selectedGroupId = null },
                        label = { Text("Все") }
                    )
                }
                items(groups) { g ->
                    FilterChip(
                        selected = selectedGroupId == g.id,
                        onClick = { selectedGroupId = g.id },
                        label = { Text(g.name) }
                    )
                }
                item {
                    AssistChip(
                        onClick = { showCreateDialog = true },
                        label = { Text("Группа") },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }

            // --- Действия с выбранной группой ---
            if (selectedGroup != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { renameTarget = selectedGroup }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Переименовать")
                    }
                    TextButton(onClick = {
                        onDeleteGroup(selectedGroup.id)
                        selectedGroupId = null
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Удалить группу")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Сохранённые маршруты А→Б (добавленные «в избранное» из планировщика)
            if (!addMode && selectedGroupId == null && savedJourneys.isNotEmpty()) {
                Text(
                    "Маршруты А→Б",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                savedJourneys.forEach { sj ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenSavedJourney(sj) },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107)) },
                        headlineContent = { Text("${sj.fromName} → ${sj.toName}") },
                        supportingContent = { Text("Построить маршрут") },
                        trailingContent = {
                            IconButton(onClick = { onDeleteSavedJourney(sj) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            when {
                addMode -> {
                    Text(
                        text = if (selectedGroup == null)
                            "Нажмите на маршрут, чтобы добавить или убрать из избранного:"
                        else
                            "Нажмите на маршрут, чтобы добавить или убрать в группе «${selectedGroup.name}»:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                        items(routes) { route ->
                            val isFav = favoriteRouteIds.contains(route.id)
                            val inGroup = selectedGroup?.routeIds?.contains(route.id) == true
                            val active = if (selectedGroup == null) isFav else inGroup
                            ListItem(
                                modifier = Modifier.clickable {
                                    if (selectedGroup == null) {
                                        onToggleFavoriteRoute(route.id)
                                    } else {
                                        // чтобы попасть в группу — должен быть в избранном
                                        if (!isFav) onToggleFavoriteRoute(route.id)
                                        onToggleRouteInGroup(selectedGroup.id, route.id)
                                    }
                                },
                                leadingContent = { RouteBadge(route) },
                                headlineContent = { Text(route.origin) },
                                supportingContent = { Text("→ ${route.destination}", maxLines = 1) },
                                trailingContent = {
                                    Icon(
                                        imageVector = if (active) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = null,
                                        tint = if (active) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }

                favRoutes.isEmpty() && favStops.isEmpty() -> {
                    Text(
                        text = if (selectedGroup == null)
                            "Пока пусто. Жми «＋ Маршрут» выше, либо добавляй остановки и маршруты звёздочкой ★ — они появятся здесь."
                        else
                            "В группе «${selectedGroup.name}» пока пусто. Нажми «＋ Маршрут», чтобы добавить сюда маршруты, либо открой остановку и добавь её в эту группу через меню «⋮».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                        if (favRoutes.isNotEmpty()) {
                            item {
                                Text(
                                    "Маршруты",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(favRoutes) { route ->
                                ListItem(
                                    modifier = Modifier.clickable { onSelectRoute(route) },
                                    leadingContent = { RouteBadge(route) },
                                    headlineContent = { Text(route.origin) },
                                    supportingContent = { Text("→ ${route.destination}", maxLines = 1) },
                                    trailingContent = {
                                        FavItemMenu(
                                            groups = groups,
                                            memberGroupIds = groups.filter { it.routeIds.contains(route.id) }.map { it.id }.toSet(),
                                            onToggleGroup = { gid -> onToggleRouteInGroup(gid, route.id) },
                                            onRemove = { onToggleFavoriteRoute(route.id) }
                                        )
                                    }
                                )
                            }
                        }
                        if (favStops.isNotEmpty()) {
                            item {
                                Text(
                                    "Остановки",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(favStops) { stop ->
                                ListItem(
                                    modifier = Modifier.clickable { onSelectStop(stop) },
                                    headlineContent = { Text(stop.name) },
                                    supportingContent = { Text("Остановка") },
                                    trailingContent = {
                                        FavItemMenu(
                                            groups = groups,
                                            memberGroupIds = groups.filter { it.stopIds.contains(stop.id) }.map { it.id }.toSet(),
                                            onToggleGroup = { gid -> onToggleStopInGroup(gid, stop.id) },
                                            onRemove = { onToggleFavoriteStop(stop.id) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        GroupNameDialog(
            title = "Новая группа",
            initial = "",
            onConfirm = { name -> onCreateGroup(name); showCreateDialog = false },
            onDismiss = { showCreateDialog = false }
        )
    }
    renameTarget?.let { g ->
        GroupNameDialog(
            title = "Переименовать группу",
            initial = g.name,
            onConfirm = { name -> onRenameGroup(g.id, name); renameTarget = null },
            onDismiss = { renameTarget = null }
        )
    }
}

@Composable
private fun RouteBadge(route: Route) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Color(route.color.toInt()), RoundedCornerShape(10.dp))
            .border(2.dp, Color(0xFF111111), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = route.number,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black,
                    blurRadius = 2f
                )
            )
        )
    }
}

@Composable
private fun FavItemMenu(
    groups: List<FavoriteGroup>,
    memberGroupIds: Set<String>,
    onToggleGroup: (String) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Действия")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (groups.isEmpty()) {
                DropdownMenuItem(
                    enabled = false,
                    text = { Text("Групп пока нет — создайте через «＋ Группа»") },
                    onClick = {}
                )
            } else {
                Text(
                    "В группах:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                groups.forEach { g ->
                    val inGroup = memberGroupIds.contains(g.id)
                    DropdownMenuItem(
                        text = { Text(g.name) },
                        leadingIcon = {
                            if (inGroup) Icon(Icons.Default.Check, contentDescription = null)
                            else Spacer(Modifier.size(24.dp))
                        },
                        onClick = { onToggleGroup(g.id) }
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Убрать из избранного") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { onRemove(); expanded = false }
            )
        }
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Название (напр. «На работу»)") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
