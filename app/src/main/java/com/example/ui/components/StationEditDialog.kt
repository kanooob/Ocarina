package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.RadioStation
import com.example.ui.LocalAppStrings
import kotlinx.coroutines.launch

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
                    text = if (existingStation == null || existingStation.id == 0L) strings.addStation else strings.editStation,
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
                        } else if (streamUrl.contains("youtube.com", ignoreCase = true) || streamUrl.contains("youtu.be", ignoreCase = true)) {
                            Text(
                                text = strings.youtubeDetectedHint,
                                color = MaterialTheme.colorScheme.primary
                            )
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
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
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
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.first) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            if (res.second.isNotBlank()) {
                                Text(
                                    text = res.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (res.first) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
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
