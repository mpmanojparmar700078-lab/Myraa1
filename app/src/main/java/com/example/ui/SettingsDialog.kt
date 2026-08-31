package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.services.MyraAccessibilityService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.UserPreferenceEntity

import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel

@Composable
fun SettingsDialog(
    customApiKey: String,
    isServiceEnabled: Boolean,
    isAccessibilityEnabled: Boolean,
    preferences: List<UserPreferenceEntity>,
    onSaveApiKey: (String) -> Unit,
    onToggleService: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(customApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var apiKeySavedFeedback by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val currentLang = preferences.find { it.key == "preferred_language" }?.value ?: "auto"

    BackHandler {
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Myra Settings & Architecture",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Gemini API Key
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Gemini API Key",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        // Upper pre-filled card removed
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                apiKeySavedFeedback = false
                            },
                            label = { Text("Custom API Key") },
                            placeholder = { Text("Enter AI Studio API Key...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input"),
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                onSaveApiKey(apiKeyInput)
                                apiKeySavedFeedback = true
                            }),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()
                                            if (clip != null && clip.text.isNotBlank()) {
                                                apiKeyInput = clip.text.trim()
                                                onSaveApiKey(clip.text.trim())
                                                apiKeySavedFeedback = true
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste from Clipboard",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle API Key Visibility"
                                        )
                                    }
                                }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clip = clipboardManager.getText()
                                    if (clip != null && clip.text.isNotBlank()) {
                                        apiKeyInput = clip.text.trim()
                                        onSaveApiKey(clip.text.trim())
                                        apiKeySavedFeedback = true
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste & Save", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    onSaveApiKey(apiKeyInput)
                                    apiKeySavedFeedback = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_api_key_button")
                            ) {
                                Text(if (apiKeySavedFeedback) "Saved ✓" else "Save Key", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Security Explanation Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Client-Side Security Notice",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Storing API keys in a client-side Android app carries exposure risks if decompiled. In production, use Firebase App Check or a backend proxy. For development, your custom key is securely isolated in app private preferences and never logged.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Section 2: Language Preference
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Preferred Language",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = currentLang == "auto",
                                onClick = { onSetLanguage("auto") },
                                label = { Text("Auto") },
                                modifier = Modifier.testTag("lang_auto_chip")
                            )
                            FilterChip(
                                selected = currentLang == "hi",
                                onClick = { onSetLanguage("hi") },
                                label = { Text("हिन्दी (Hindi)") },
                                modifier = Modifier.testTag("lang_hi_chip")
                            )
                            FilterChip(
                                selected = currentLang == "en",
                                onClick = { onSetLanguage("en") },
                                label = { Text("English") },
                                modifier = Modifier.testTag("lang_en_chip")
                            )
                        }
                    }
                }

                // Section 3: Foreground Background Service
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Persistent Assistant Layer",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Keeps Myra active in background via Android Foreground Service when YouTube/Chrome are in foreground.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isServiceEnabled,
                                onCheckedChange = onToggleService,
                                modifier = Modifier.testTag("service_toggle_switch")
                            )
                        }
                    }
                }

                // Section 4: Android Accessibility Service (V3.1 Foundation)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Accessibility Service",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            // Accessibility Status Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAccessibilityEnabled) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isAccessibilityEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Text(
                                        text = if (isAccessibilityEnabled) "Enabled" else "Disabled",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAccessibilityEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Accessibility permission allows Myra to read visible interface information and perform supported actions when you explicitly ask it to.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enable_accessibility_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAccessibilityEnabled) "Open Accessibility Settings" else "Enable Accessibility",
                                fontSize = 13.sp
                            )
                        }

                        // Local Privacy Guarantee Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = "Privacy: Screen data stays 100% on-device. Passwords & OTPs are excluded, and screen contents are never sent to AI models automatically.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                // Section 5: Saved Memory
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Local Memory (Room DB)",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        if (preferences.isEmpty()) {
                            Text(
                                text = "No preferences stored yet. (e.g., try 'मुझसे हमेशा हिंदी में बात करना')",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            preferences.forEach { pref ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = pref.key,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = pref.value,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("settings_close_button")
            ) {
                Text("Close")
            }
        }
    )
}
