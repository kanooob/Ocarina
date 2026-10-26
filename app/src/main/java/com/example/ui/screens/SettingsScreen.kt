package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppLanguage
import com.example.data.RadioStation
import com.example.data.ThemeMode
import com.example.ui.LocalAppStrings
import com.example.ui.RadioViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RadioViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }
    val strings = LocalAppStrings.current

    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val selectedStation by viewModel.selectedStation.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val skipStreamReload by viewModel.skipStreamReload.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var stationToEdit by remember { mutableStateOf<RadioStation?>(null) }
    var stationToDelete by remember { mutableStateOf<RadioStation?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.settingsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.cancel
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // Bouton d'ajout flottant surélevé pour éviter les conflits gestuels avec la barre système
            FloatingActionButton(
                onClick = {
                    stationToEdit = null
                    showEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 24.dp, end = 4.dp)
                    .testTag("fab_add_station")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = strings.addStation
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Thème d'affichage
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BrightnessAuto,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = strings.displayThemeTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        SelectableOptionRow(
                            title = strings.themeFollowSystem,
                            icon = Icons.Default.BrightnessAuto,
                            isSelected = themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                            testTag = "theme_system_option"
                        )
                        SelectableOptionRow(
                            title = strings.themeLight,
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                            testTag = "theme_light_option"
                        )
                        SelectableOptionRow(
                            title = strings.themeDark,
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == ThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                            testTag = "theme_dark_option"
                        )
                    }
                }
            }

            // Section 2: Langue de l'application
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = strings.appLanguageTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        SelectableOptionRow(
                            title = strings.langFollowSystem,
                            icon = Icons.Default.Language,
                            isSelected = language == AppLanguage.SYSTEM,
                            onClick = { viewModel.setLanguage(AppLanguage.SYSTEM) },
                            testTag = "lang_system_option"
                        )
                        SelectableOptionRow(
                            title = strings.langFrench,
                            icon = Icons.Default.Language,
                            isSelected = language == AppLanguage.FRENCH,
                            onClick = { viewModel.setLanguage(AppLanguage.FRENCH) },
                            testTag = "lang_fr_option"
                        )
                        SelectableOptionRow(
                            title = strings.langEnglish,
                            icon = Icons.Default.Language,
                            isSelected = language == AppLanguage.ENGLISH,
                            onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                            testTag = "lang_en_option"
                        )
                    }
                }
            }

            // Section 3: Contrôle du flux au skip (Nouveau paramètre)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = strings.skipControlTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Switch(
                                checked = skipStreamReload,
                                onCheckedChange = { viewModel.setSkipStreamReload(it) },
                                modifier = Modifier.testTag("skip_reload_switch")
                            )
                        }

                        Text(
                            text = strings.skipControlDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Section 4: Information Système (Lecteur système & Matériel connecté)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headset,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.systemPopupInfoTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = strings.systemPopupInfoDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Espacement vertical aéré sous le bloc Lecteur système & Matériel connecté
            item {
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Section 5: Titre de la Base de données locale dans un conteneur arrondi cohérent
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = strings.stationsManagement,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "${stations.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = strings.stationsManagementDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (stations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = strings.emptyStationsList,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.emptyStationsHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(stations, key = { it.id }) { station ->
                    val isSelected = station.id == selectedStation?.id
                    StationItemCard(
                        station = station,
                        isSelected = isSelected,
                        onSelect = {
                            viewModel.selectAndPlayStation(station)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Station '${station.name}' activée")
                            }
                        },
                        onPinShortcut = {
                            val pinned = viewModel.pinStationShortcut(station)
                            coroutineScope.launch {
                                if (pinned) {
                                    snackbarHostState.showSnackbar("Raccourci pour '${station.name}' créé sur l'écran d'accueil")
                                } else {
                                    snackbarHostState.showSnackbar("Raccourci disponible en appui long sur l'icône de l'app")
                                }
                            }
                        },
                        onEdit = {
                            stationToEdit = station
                            showEditDialog = true
                        },
                        onDelete = {
                            stationToDelete = station
                        }
                    )
                }
            }

            // Pied de page : Mention de l'année 2026
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = strings.footerCopyright,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(70.dp))
            }
        }
    }

    // Modal dialogue pour ajouter / modifier une station (ergonomie aérée, hiérarchie de boutons nette)
    if (showEditDialog) {
        StationEditDialog(
            existingStation = stationToEdit,
            onDismiss = { showEditDialog = false },
            onSave = { name, streamUrl, changeSongUrl ->
                if (stationToEdit == null) {
                    viewModel.addStation(name, streamUrl, changeSongUrl) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Station '$name' ajoutée avec succès")
                        }
                    }
                } else {
                    val updated = stationToEdit!!.copy(
                        name = name,
                        streamUrl = streamUrl,
                        changeSongUrl = changeSongUrl
                    )
                    viewModel.updateStation(updated)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Station '$name' mise à jour")
                    }
                }
                showEditDialog = false
            },
            onTestStream = { testUrl ->
                viewModel.testStreamUrl(testUrl)
            }
        )
    }

    // Dialogue de confirmation de suppression
    stationToDelete?.let { station ->
        AlertDialog(
            onDismissRequest = { stationToDelete = null },
            title = {
                Text(
                    text = strings.deleteStationConfirmTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = String.format(strings.deleteStationConfirmMsg, station.name),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStation(station)
                        stationToDelete = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Station '${station.name}' supprimée")
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { stationToDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun SelectableOptionRow(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun StationItemCard(
    station: RadioStation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPinShortcut: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("station_card_${station.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.PlayArrow else Icons.Default.Radio,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Text(
                                text = strings.activeStation,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row {
                    IconButton(
                        onClick = onPinShortcut,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Épingler le raccourci sur l'écran d'accueil",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = strings.editStation,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = strings.deleteStation,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // URL du flux affichée avec bon contraste et sans coupure
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = station.streamUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (station.changeSongUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = station.changeSongUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun StationEditDialog(
    existingStation: RadioStation?,
    onDismiss: () -> Unit,
    onSave: (name: String, streamUrl: String, changeSongUrl: String) -> Unit,
    onTestStream: suspend (String) -> Pair<Boolean, String>
) {
    val strings = LocalAppStrings.current
    var name by remember { mutableStateOf(existingStation?.name ?: "") }
    var streamUrl by remember { mutableStateOf(existingStation?.streamUrl ?: "") }
    var changeSongUrl by remember { mutableStateOf(existingStation?.changeSongUrl ?: "") }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var nameError by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }
    var urlErrorMessage by remember { mutableStateOf("") }

    fun validateAndCleanStreamUrl(input: String): Pair<Boolean, String> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return Pair(false, "")
        }
        val effective = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            if (trimmed.contains(".") && !trimmed.contains(" ")) {
                "http://$trimmed"
            } else {
                return Pair(false, "")
            }
        } else {
            trimmed
        }
        return try {
            val uri = android.net.Uri.parse(effective)
            val scheme = uri.scheme
            val host = uri.host
            val isValid = (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) &&
                    !host.isNullOrBlank() && host.contains(".") && !host.contains(" ")
            if (isValid) Pair(true, effective) else Pair(false, "")
        } catch (e: Exception) {
            Pair(false, "")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingStation == null) strings.addStation else strings.editStation,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.cancel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Nom de la station (Obligatoire)
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError && it.isNotBlank()) nameError = false
                    },
                    label = { Text(strings.stationNameLabel) },
                    placeholder = { Text(strings.stationNamePlaceholder) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = {
                        if (nameError) Text(strings.stationNameError)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("station_name_input")
                )

                // URL du flux (Obligatoire)
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = {
                        streamUrl = it
                        testResult = null
                        if (urlError) urlError = false
                    },
                    label = { Text(strings.streamUrlLabel) },
                    placeholder = { Text(strings.streamUrlPlaceholder) },
                    singleLine = true,
                    isError = urlError,
                    supportingText = {
                        if (urlError) {
                            Text(urlErrorMessage.ifBlank { strings.streamUrlError })
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_url_input")
                )

                // URL pour changer de musique avec espacement sous le champ
                Column {
                    OutlinedTextField(
                        value = changeSongUrl,
                        onValueChange = { changeSongUrl = it },
                        label = { Text(strings.changeSongUrlLabel) },
                        placeholder = { Text(strings.changeSongUrlPlaceholder) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_song_url_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.changeSongUrlHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Bouton secondaire de test avec contour
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val (canTest, cleanTestUrl) = validateAndCleanStreamUrl(streamUrl)
                            if (canTest) {
                                isTesting = true
                                testResult = null
                                coroutineScope.launch {
                                    val result = onTestStream(cleanTestUrl)
                                    testResult = result
                                    isTesting = false
                                }
                            } else {
                                urlError = true
                                urlErrorMessage = if (streamUrl.isBlank()) strings.streamUrlError else strings.streamUrlInvalid
                            }
                        },
                        enabled = !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.testing)
                        } else {
                            Text(strings.testStream)
                        }
                    }

                    testResult?.let { res ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (res.first) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (res.first) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (res.first) strings.accessible else strings.inaccessible,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (res.first) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Bouton principal d'action plein
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val (isValidUrl, effectiveUrl) = validateAndCleanStreamUrl(streamUrl)
                    nameError = trimmedName.isBlank()
                    urlError = !isValidUrl
                    if (!isValidUrl) {
                        urlErrorMessage = if (streamUrl.isBlank()) strings.streamUrlError else strings.streamUrlInvalid
                    }
                    if (!nameError && !urlError) {
                        val rawChange = changeSongUrl.trim()
                        val effectiveChange = if (rawChange.isNotBlank() && !rawChange.startsWith("http://", ignoreCase = true) && !rawChange.startsWith("https://", ignoreCase = true)) {
                            if (rawChange.contains(".") && !rawChange.contains(" ")) "http://$rawChange" else rawChange
                        } else rawChange

                        onSave(trimmedName, effectiveUrl, effectiveChange)
                    }
                },
                modifier = Modifier.testTag("save_station_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
