package com.example.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.R
import com.example.data.MessageEntity
import com.example.models.AssistantState
import com.example.platform.android.PermissionManager
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Indigo500
import com.example.viewmodels.AssistantViewModel
import com.example.voice.SpeechState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val assistantState by viewModel.assistantState.collectAsState()
    val speechState by viewModel.speechState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val speechLanguage by viewModel.speechLanguage.collectAsState()
    val isVoiceOutputEnabled by viewModel.isVoiceOutputEnabled.collectAsState()
    val isForegroundServiceActive by viewModel.isForegroundServiceActive.collectAsState()
    val isAutoLearningEnabled by viewModel.isAutoLearningEnabled.collectAsState()
    val isGeminiFallbackEnabled by viewModel.isGeminiFallbackEnabled.collectAsState()
    val customApiKey by viewModel.customApiKey.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    val learnedSkills by viewModel.learnedSkills.collectAsState()
    val experiences by viewModel.experiences.collectAsState()
    val skillCount by viewModel.skillCount.collectAsState()
    val experienceCount by viewModel.experienceCount.collectAsState()
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsState()
    val apiKeyValidationState by viewModel.apiKeyValidationState.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showBrainDialog by remember { mutableStateOf(false) }
    var showAppsDialog by remember { mutableStateOf(false) }
    var showApiDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var isMicGranted by remember { mutableStateOf(PermissionManager.isMicrophoneGranted(context)) }
    var isNotifGranted by remember { mutableStateOf(PermissionManager.isNotificationGranted(context)) }
    var isAccessibilityEnabled by remember { mutableStateOf(PermissionManager.isAccessibilityServiceEnabled(context)) }
    var isBatteryOptimized by remember { mutableStateOf(PermissionManager.isBatteryOptimizationIgnored(context)) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isMicGranted = isGranted
        if (isGranted) {
            try {
                viewModel.startListening()
            } catch (e: Exception) {
                // Ignore
            }
        } else {
            showPermissionsDialog = true
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isNotifGranted = isGranted
    }

    val speechInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                viewModel.onSpeechResult(matches[0])
            }
        }
    }

    val sttErrorMessage by viewModel.sttErrorMessage.collectAsState()

    LifecycleResumeEffect(Unit) {
        isMicGranted = PermissionManager.isMicrophoneGranted(context)
        isNotifGranted = PermissionManager.isNotificationGranted(context)
        isAccessibilityEnabled = PermissionManager.isAccessibilityServiceEnabled(context)
        isBatteryOptimized = PermissionManager.isBatteryOptimizationIgnored(context)
        onPauseOrDispose { }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = when (assistantState) {
                                AssistantState.PROCESSING -> Cyan400
                                AssistantState.EXECUTING_ACTION -> Emerald500
                                AssistantState.ERROR -> MaterialTheme.colorScheme.error
                                else -> Cyan400
                            },
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.assistant_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (assistantState) {
                                    AssistantState.IDLE -> assistantState.descriptionHindi
                                    AssistantState.PROCESSING -> assistantState.descriptionHindi
                                    AssistantState.EXECUTING_ACTION -> assistantState.descriptionHindi
                                    AssistantState.CANCELLING, AssistantState.CANCELLED -> assistantState.descriptionHindi
                                    else -> assistantState.label
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (assistantState) {
                                    AssistantState.PROCESSING -> Cyan400
                                    AssistantState.EXECUTING_ACTION -> Emerald500
                                    AssistantState.ERROR -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showBrainDialog = true },
                        modifier = Modifier.testTag("brain_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Myra Brain",
                            tint = if (skillCount > 0 || experienceCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showAppsDialog = true },
                        modifier = Modifier.testTag("apps_button")
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = "Installed Apps")
                    }
                    IconButton(
                        onClick = { showApiDialog = true },
                        modifier = Modifier.testTag("api_explorer_button")
                    ) {
                        Icon(Icons.Default.Build, contentDescription = "Automation Tester")
                    }
                    IconButton(
                        onClick = { viewModel.clearChatHistory() },
                        modifier = Modifier.testTag("clear_chat_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Chat")
                    }
                    IconButton(
                        onClick = { showPermissionsDialog = true },
                        modifier = Modifier.testTag("permissions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = "Accessibility & Permissions",
                            tint = if (!isAccessibilityEnabled || !isMicGranted) MaterialTheme.colorScheme.error else Emerald500
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
                    .padding(8.dp)
            ) {
                // Quick Suggestion Chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    val chips = listOf(
                        "YouTube खोलो",
                        "YouTube पर गाने चलाओ",
                        "Chrome खोलो",
                        "Camera खोलो",
                        "होम स्क्रीन जाओ",
                        "Settings खोलो"
                    )
                    items(chips) { chip ->
                        SuggestionChip(
                            onClick = {
                                viewModel.onInputTextChanged(chip)
                                viewModel.sendMessage()
                            },
                            label = { Text(chip, fontSize = 12.sp) }
                        )
                    }
                }

                if (!sttErrorMessage.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🎤 $sttErrorMessage",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                try {
                                    speechInputLauncher.launch(viewModel.getSpeechIntent())
                                } catch (e: Exception) {
                                    showPermissionsDialog = true
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("सिस्टम वॉयस से बोलें", fontSize = 11.sp)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoiceMicButton(
                        speechState = speechState,
                        onStartListening = {
                            isMicGranted = PermissionManager.isMicrophoneGranted(context)
                            if (!isMicGranted) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                try {
                                    viewModel.startListening()
                                } catch (e: Exception) {
                                    try {
                                        speechInputLauncher.launch(viewModel.getSpeechIntent())
                                    } catch (ex: Exception) {
                                        showPermissionsDialog = true
                                    }
                                }
                            }
                        },
                        onStopListening = { viewModel.stopListening() }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { viewModel.onInputTextChanged(it) },
                        placeholder = {
                            Text(
                                stringResource(R.string.input_placeholder),
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_input_field")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (assistantState == AssistantState.PROCESSING || assistantState == AssistantState.EXECUTING_ACTION) {
                        IconButton(
                            onClick = { viewModel.cancelCurrentTask() },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                                .testTag("cancel_button")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel Task",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .background(
                                    if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                                .testTag("send_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!isAccessibilityEnabled || !isMicGranted) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { showPermissionsDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (!isAccessibilityEnabled && !isMicGranted) "एक्सेसिबिलिटी और माइक अनुमति आवश्यक है"
                                    else if (!isAccessibilityEnabled) "एक्सेसिबिलिटी सर्विस चालू नहीं है"
                                    else "माइक्रोफोन अनुमति आवश्यक है",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "टैप करें और आवश्यक अनुमतियाँ चालू करें",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }
                            Button(
                                onClick = { showPermissionsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("सेट करें", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (messages.isEmpty()) {
                        EmptyStateView(
                            onSampleClick = {
                                viewModel.onInputTextChanged(it)
                                viewModel.sendMessage()
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageItem(
                                    message = message,
                                    onSpeak = { viewModel.speak(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBrainDialog) {
        LearnedBrainDialog(
            skills = learnedSkills,
            experiences = experiences,
            diagnosticLogs = diagnosticLogs,
            onDeleteSkill = { viewModel.deleteSkill(it) },
            onDeleteExperience = { viewModel.deleteExperience(it) },
            onResetAll = {
                viewModel.resetLearnedBrain()
                showBrainDialog = false
            },
            onDismiss = { showBrainDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentApiKey = customApiKey,
            currentLanguage = speechLanguage,
            isVoiceOutputEnabled = isVoiceOutputEnabled,
            isForegroundServiceActive = isForegroundServiceActive,
            isAutoLearningEnabled = isAutoLearningEnabled,
            isGeminiFallbackEnabled = isGeminiFallbackEnabled,
            skillCount = skillCount,
            experienceCount = experienceCount,
            apiKeyValidationState = apiKeyValidationState,
            onSaveApiKey = { viewModel.setCustomApiKey(it) },
            onValidateAndSaveApiKey = { viewModel.validateAndSaveApiKey(it) },
            onResetValidationState = { viewModel.resetApiKeyValidationState() },
            onSelectLanguage = { viewModel.setSpeechLanguage(it) },
            onToggleVoiceOutput = { viewModel.setVoiceOutputEnabled(it) },
            onToggleForegroundService = { viewModel.setForegroundServiceEnabled(it) },
            onToggleAutoLearning = { viewModel.setAutoLearningEnabled(it) },
            onToggleGeminiFallback = { viewModel.setGeminiFallbackEnabled(it) },
            onOpenBrainDialog = {
                showSettingsDialog = false
                showBrainDialog = true
            },
            onOpenPermissionsDialog = {
                showSettingsDialog = false
                showPermissionsDialog = true
            },
            onSaveTtsSettings = { rate, pitch ->
                viewModel.setTtsSpeechRate(rate)
                viewModel.setTtsPitch(pitch)
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showPermissionsDialog) {
        PermissionsDialog(
            isAccessibilityEnabled = isAccessibilityEnabled,
            isMicrophoneGranted = isMicGranted,
            isNotificationGranted = isNotifGranted,
            isBatteryOptimized = isBatteryOptimized,
            onRequestMicrophone = {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onRequestNotification = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionManager.openNotificationSettings(context)
                }
            },
            onDismiss = {
                showPermissionsDialog = false
                isMicGranted = PermissionManager.isMicrophoneGranted(context)
                isNotifGranted = PermissionManager.isNotificationGranted(context)
                isAccessibilityEnabled = PermissionManager.isAccessibilityServiceEnabled(context)
                isBatteryOptimized = PermissionManager.isBatteryOptimizationIgnored(context)
            }
        )
    }

    if (showAppsDialog) {
        InstalledAppsDialog(
            apps = installedApps,
            onLaunchApp = { viewModel.launchApp(it) },
            onDismiss = { showAppsDialog = false }
        )
    }

    if (showApiDialog) {
        ApiExplorerDialog(
            onTestQuery = {
                viewModel.onInputTextChanged(it)
                viewModel.sendMessage()
            },
            onDismiss = { showApiDialog = false }
        )
    }
}

@Composable
fun MessageItem(
    message: MessageEntity,
    onSpeak: (String) -> Unit
) {
    val isUser = message.isUser

    Row(
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (message.actionType != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Indigo500.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = message.actionType,
                                        fontSize = 10.sp,
                                        color = Indigo500,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (message.executionSource != null) {
                                val sourceText = when (message.executionSource) {
                                    "LOCAL_PARSER", "LOCAL" -> "Local"
                                    "LEARNED_SKILL" -> "Skill"
                                    "EXPERIENCE_MEMORY" -> "Memory"
                                    "GEMINI_FALLBACK" -> "Gemini"
                                    else -> message.executionSource
                                }
                                val sourceColor = when (message.executionSource) {
                                    "LOCAL_PARSER", "LOCAL" -> Emerald500
                                    "LEARNED_SKILL" -> Cyan400
                                    "EXPERIENCE_MEMORY" -> Indigo500
                                    "GEMINI_FALLBACK" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.outline
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = sourceColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = sourceText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = sourceColor,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onSpeak(message.text) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Read Aloud",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(onSampleClick: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("M", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Myra Assistant",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.assistant_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Try asking (बोलें या लिखें):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        listOf(
            "\"YouTube खोलो\"",
            "\"YouTube par gaane chalao\"",
            "\"Chrome खोलो\"",
            "\"Camera खोलो\"",
            "\"Home screen jao\""
        ).forEach { sample ->
            Text(
                text = sample,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSampleClick(sample.replace("\"", "")) }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )
        }
    }
}
