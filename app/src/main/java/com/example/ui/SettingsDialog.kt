package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.voice.SpeechLanguage

@Composable
fun SettingsDialog(
    currentApiKey: String,
    currentLanguage: SpeechLanguage,
    isVoiceOutputEnabled: Boolean,
    isForegroundServiceActive: Boolean,
    onSaveApiKey: (String) -> Unit,
    onSelectLanguage: (SpeechLanguage) -> Unit,
    onToggleVoiceOutput: (Boolean) -> Unit,
    onToggleForegroundService: (Boolean) -> Unit,
    onSaveTtsSettings: (rate: Float, pitch: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyText by remember { mutableStateOf(currentApiKey) }
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }
    var voiceOutput by remember { mutableStateOf(isVoiceOutputEnabled) }
    var serviceActive by remember { mutableStateOf(isForegroundServiceActive) }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var pitch by remember { mutableFloatStateOf(1.0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Myra Settings", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Gemini API Key (Optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    placeholder = { Text("AI Studio Gemini Key...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("api_key_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Assistant Language",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                SpeechLanguage.entries.forEach { lang ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selectedLanguage == lang,
                            onClick = {
                                selectedLanguage = lang
                                onSelectLanguage(lang)
                            }
                        )
                        Text(
                            text = lang.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Voice Output (TTS)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Speak assistant responses aloud",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = voiceOutput,
                        onCheckedChange = {
                            voiceOutput = it
                            onToggleVoiceOutput(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Service", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Keep Myra ready in background notification",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = serviceActive,
                        onCheckedChange = {
                            serviceActive = it
                            onToggleForegroundService(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Speech Rate (${String.format("%.1f", speechRate)}x)", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = speechRate,
                    onValueChange = {
                        speechRate = it
                        onSaveTtsSettings(speechRate, pitch)
                    },
                    valueRange = 0.5f..2.0f
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveApiKey(apiKeyText.trim())
                    onDismiss()
                },
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
