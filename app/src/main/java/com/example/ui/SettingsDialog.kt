package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.ApiKeyValidationState
import com.example.ui.theme.Emerald500
import com.example.voice.SpeechLanguage

@Composable
fun SettingsDialog(
    currentApiKey: String,
    currentLanguage: SpeechLanguage,
    isVoiceOutputEnabled: Boolean,
    isForegroundServiceActive: Boolean,
    isAutoLearningEnabled: Boolean,
    isGeminiFallbackEnabled: Boolean,
    skillCount: Int,
    experienceCount: Int,
    apiKeyValidationState: ApiKeyValidationState = ApiKeyValidationState.Idle,
    onSaveApiKey: (String) -> Unit,
    onValidateAndSaveApiKey: (String) -> Unit = {},
    onResetValidationState: () -> Unit = {},
    onSelectLanguage: (SpeechLanguage) -> Unit,
    onToggleVoiceOutput: (Boolean) -> Unit,
    onToggleForegroundService: (Boolean) -> Unit,
    onToggleAutoLearning: (Boolean) -> Unit,
    onToggleGeminiFallback: (Boolean) -> Unit,
    onOpenBrainDialog: () -> Unit,
    onOpenPermissionsDialog: () -> Unit,
    onSaveTtsSettings: (rate: Float, pitch: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyText by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var hasLocallySaved by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    var selectedLanguage by remember { mutableStateOf(currentLanguage) }
    var voiceOutput by remember { mutableStateOf(isVoiceOutputEnabled) }
    var serviceActive by remember { mutableStateOf(isForegroundServiceActive) }
    var autoLearning by remember { mutableStateOf(isAutoLearningEnabled) }
    var geminiFallback by remember { mutableStateOf(isGeminiFallbackEnabled) }
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
                // --- PERMISSIONS & SYSTEM ACCESS ---
                Text(
                    text = "सिस्टम अनुमतियाँ और सेवाएं (Permissions)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        onDismiss()
                        onOpenPermissionsDialog()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.AccessibilityNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accessibility & Mic अनुमति जांचें")
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                // --- BRAIN / SELF-LEARNING SECTION ---
                Text(
                    text = "Local-First Self-Learning Brain",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic Learning", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Learn repeated workflows & patterns locally",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoLearning,
                        onCheckedChange = {
                            autoLearning = it
                            onToggleAutoLearning(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gemini Fallback", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Only call Gemini when local brain cannot solve",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = geminiFallback,
                        onCheckedChange = {
                            geminiFallback = it
                            onToggleGeminiFallback(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onOpenBrainDialog,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Inspect Brain ($skillCount Skills, $experienceCount Memories)")
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // --- GEMINI API KEY SECTION ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gemini API Key",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (apiKeyText.isNotBlank()) "की दर्ज है (${apiKeyText.take(6)}...)" else "Google AI Studio से API Key दर्ज करें",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = {
                        apiKeyText = it
                        hasLocallySaved = false
                        onResetValidationState()
                    },
                    placeholder = { Text("AIzaSy... पेस्ट करें", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isKeyVisible) "Hide Key" else "Show Key"
                                )
                            }
                            if (apiKeyText.isNotBlank()) {
                                IconButton(onClick = {
                                    apiKeyText = ""
                                    hasLocallySaved = false
                                    onSaveApiKey("")
                                    onResetValidationState()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear Key")
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("api_key_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons: Confirm & Test, Save Directly, Paste
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Confirm & Test Button
                    Button(
                        onClick = {
                            val trimmed = apiKeyText.trim()
                            if (trimmed.isNotBlank()) {
                                onSaveApiKey(trimmed)
                                onValidateAndSaveApiKey(trimmed)
                            }
                        },
                        enabled = apiKeyText.isNotBlank() && apiKeyValidationState !is ApiKeyValidationState.Validating,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("confirm_api_key_button")
                    ) {
                        if (apiKeyValidationState is ApiKeyValidationState.Validating) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("जाँच रहे हैं...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("कन्फर्म और टेस्ट", fontSize = 11.sp)
                        }
                    }

                    // Direct Save Button
                    OutlinedButton(
                        onClick = {
                            val trimmed = apiKeyText.trim()
                            onSaveApiKey(trimmed)
                            hasLocallySaved = true
                        },
                        enabled = apiKeyText.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("direct_save_key_button")
                    ) {
                        Text("सेव करें", fontSize = 11.sp)
                    }

                    // Paste Button
                    OutlinedButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                apiKeyText = clip.trim()
                                hasLocallySaved = false
                                onResetValidationState()
                            }
                        },
                        modifier = Modifier.testTag("paste_key_button")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(16.dp))
                    }
                }

                // Feedback UI
                if (hasLocallySaved && apiKeyValidationState !is ApiKeyValidationState.Success && apiKeyValidationState !is ApiKeyValidationState.Error) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "API Key फ़ोन मेमोरी में सेव हो गई है!",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                when (val state = apiKeyValidationState) {
                    is ApiKeyValidationState.Success -> {
                        Surface(
                            color = Emerald500.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald500, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    state.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Emerald500,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    is ApiKeyValidationState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    state.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // --- LANGUAGE SECTION ---
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

                // --- VOICE OUTPUT SECTION ---
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

                // --- BACKGROUND SERVICE SECTION ---
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
