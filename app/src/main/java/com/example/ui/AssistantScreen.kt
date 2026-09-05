package com.example.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.voice.SpeechLanguage
import com.example.voice.SpeechState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.MessageEntity
import com.example.models.AssistantState
import com.example.viewmodels.AssistantViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val currentActionProgress by viewModel.currentActionProgress.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val serviceEnabled by viewModel.serviceEnabled.collectAsStateWithLifecycle()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val apis by viewModel.apis.collectAsStateWithLifecycle()
    val apiCallHistory by viewModel.apiCallHistory.collectAsStateWithLifecycle()
    val apiTestResult by viewModel.apiTestResult.collectAsStateWithLifecycle()
    val isTestingApi by viewModel.isTestingApi.collectAsStateWithLifecycle()

    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val activeTask by viewModel.activeTask.collectAsStateWithLifecycle()
    val schedulerEnabled by viewModel.schedulerEnabled.collectAsStateWithLifecycle()
    val schedulerIntervalMinutes by viewModel.schedulerIntervalMinutes.collectAsStateWithLifecycle()
    val schedulerLastRun by viewModel.schedulerLastRunTimestamp.collectAsStateWithLifecycle()
    val schedulerLastSummary by viewModel.schedulerLastRunSummary.collectAsStateWithLifecycle()
    val schedulerWorkStatus by viewModel.schedulerWorkStatus.collectAsStateWithLifecycle()

    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val speechSoundLevel by viewModel.speechSoundLevel.collectAsStateWithLifecycle()
    val speechError by viewModel.speechError.collectAsStateWithLifecycle()
    val isHandsFreeVoiceEnabled by viewModel.isHandsFreeVoiceEnabled.collectAsStateWithLifecycle()
    val selectedSpeechLanguage by viewModel.selectedSpeechLanguage.collectAsStateWithLifecycle()

    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val isVoiceOutputEnabled by viewModel.isVoiceOutputEnabled.collectAsStateWithLifecycle()
    val ttsSpeechRate by viewModel.ttsSpeechRate.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(viewModel.permissionManager.hasMicrophonePermission())
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.startVoiceListening()
        }
    }

    val handleVoiceClick: () -> Unit = {
        if (viewModel.permissionManager.hasMicrophonePermission()) {
            if (speechState == SpeechState.LISTENING) {
                viewModel.stopVoiceListening()
            } else {
                viewModel.startVoiceListening()
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var inputText by remember { mutableStateOf("") }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showInstalledAppsSheet by remember { mutableStateOf(false) }
    var showApiExplorerDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showInstalledAppsSheet) {
        InstalledAppsSheet(
            installedApps = installedApps,
            isLoading = isLoadingApps,
            onRefresh = { viewModel.loadInstalledApps() },
            onLaunchViaTextCommand = { command ->
                viewModel.sendCommand(command)
            },
            onDismiss = { showInstalledAppsSheet = false }
        )
    }

    if (showApiExplorerDialog) {
        ApiExplorerDialog(
            apis = apis,
            callHistory = apiCallHistory,
            testResult = apiTestResult,
            isTesting = isTestingApi,
            onToggleApi = { id, enabled -> viewModel.toggleApiEnabled(id, enabled) },
            onTestApi = { api -> viewModel.testApi(api) },
            onClearTestResult = { viewModel.clearApiTestResult() },
            onReloadCatalog = { viewModel.reloadApiCatalog() },
            onDismiss = { showApiExplorerDialog = false }
        )
    }

    if (showSettingsScreen) {
        SettingsScreen(
            customApiKey = customApiKey,
            isServiceEnabled = serviceEnabled,
            isAccessibilityEnabled = isAccessibilityEnabled,
            preferences = preferences,
            capabilities = capabilities,
            isWorkSchedulerEnabled = schedulerEnabled,
            workSchedulerInterval = schedulerIntervalMinutes,
            workSchedulerStatus = schedulerWorkStatus,
            workSchedulerLastRun = schedulerLastRun,
            workSchedulerLastSummary = schedulerLastSummary,
            onSaveApiKey = { viewModel.saveCustomApiKey(it) },
            onToggleService = { viewModel.setForegroundServiceEnabled(it) },
            onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
            onSetLanguage = { viewModel.updateLanguagePreference(it) },
            onToggleBackgroundListening = { viewModel.setBackgroundListeningEnabled(it) },
            onToggleGameInteraction = { viewModel.setGameInteractionEnabled(it) },
            onToggleAutoMode = { viewModel.setAutoMode(it) },
            onToggleAskBeforeActions = { viewModel.setAskBeforeActions(it) },
            onToggleWorkScheduler = { viewModel.setWorkSchedulerEnabled(it) },
            onChangeWorkSchedulerInterval = { viewModel.setWorkSchedulerInterval(it) },
            onRunImmediateWorkScheduler = { viewModel.runImmediateWorkScheduler() },
            isHandsFreeVoiceEnabled = isHandsFreeVoiceEnabled,
            selectedSpeechLanguage = selectedSpeechLanguage,
            isMicrophonePermissionGranted = hasMicPermission,
            isSpeechRecognitionAvailable = viewModel.isSpeechRecognitionAvailable,
            onToggleHandsFreeVoice = { viewModel.setHandsFreeVoiceEnabled(it) },
            onSelectSpeechLanguage = { viewModel.setSpeechLanguage(it) },
            onRequestMicrophonePermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            isVoiceOutputEnabled = isVoiceOutputEnabled,
            ttsSpeechRate = ttsSpeechRate,
            onToggleVoiceOutput = { viewModel.setVoiceOutputEnabled(it) },
            onChangeTtsSpeechRate = { viewModel.setTtsSpeechRate(it) },
            onTestVoiceOutput = { viewModel.speakText("नमस्ते! मैं मायरा हूँ, आपकी एआई सहायक।", force = true) },
            onOpenInstalledApps = {
                viewModel.loadInstalledApps()
                showInstalledAppsSheet = true
            },
            onOpenApiCatalog = {
                showApiExplorerDialog = true
            },
            onClearAllData = {
                viewModel.clearChatHistory()
            },
            onNavigateBack = { showSettingsScreen = false }
        )
        return
    }

    if (showClearConfirmDialog) {
        BackHandler {
            showClearConfirmDialog = false
        }
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Conversation?") },
            text = { Text("This will delete all message history from local memory.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChatHistory()
                        showClearConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_button")
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Glowing Orb Avatar
                        MyraOrbAvatar(state = assistantState)

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Myra",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                AssistantStatusBadge(state = assistantState)
                            }
                            Text(
                                text = "Persistent AI Assistant Layer (V2)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsScreen = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Suggestion Chips Bar
            SuggestionChipsRow(
                onSelectChip = { query ->
                    viewModel.sendCommand(query)
                }
            )

            // Active Autonomous Task Banner with Stop / Cancel Control
            AnimatedVisibility(
                visible = activeTask != null && activeTask?.isRunning == true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                activeTask?.let { task ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("active_task_banner"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Task Active • ${task.state.name}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = task.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            FilledTonalButton(
                                onClick = { viewModel.cancelAutonomousTask() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.testTag("cancel_task_button")
                            ) {
                                Text("Stop", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Conversation Messages Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    EmptyConversationState(
                        onSuggestionClick = { viewModel.sendCommand(it) },
                        onOpenSettings = {
                            showSettingsScreen = true
                        },
                        onStartVoice = handleVoiceClick
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            MessageBubble(
                                message = msg,
                                isSpeaking = isTtsSpeaking,
                                onSpeak = { text -> viewModel.speakText(text, force = true) },
                                onStopSpeaking = { viewModel.stopSpeaking() }
                            )
                        }
                    }
                }
            }

            // Live Action Progress Card if executing multi-step actions
            AnimatedVisibility(
                visible = currentActionProgress != null,
                enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
            ) {
                currentActionProgress?.let { progress ->
                    LiveActionProgressBanner(
                        progress = progress,
                        onCancel = { viewModel.cancelCurrentRequest() }
                    )
                }
            }

            // Live Voice Output (Speaking) Banner
            LiveVoiceSpeakingBanner(
                isSpeaking = isTtsSpeaking,
                onStopSpeaking = { viewModel.stopSpeaking() }
            )

            // Live Speech-to-Text & Hands-Free Listening Banner
            LiveVoiceListeningBanner(
                speechState = speechState,
                partialTranscript = partialTranscript,
                soundLevel = speechSoundLevel,
                errorMessage = speechError,
                isHandsFreeEnabled = isHandsFreeVoiceEnabled,
                selectedLanguage = selectedSpeechLanguage,
                onStopListening = { viewModel.stopVoiceListening() },
                onCancelListening = { viewModel.cancelVoiceListening() },
                onSendNow = { cmd ->
                    viewModel.stopVoiceListening()
                    if (cmd.isNotBlank()) {
                        viewModel.sendCommand(cmd)
                    }
                }
            )

            // Bottom Input Bar
            BottomCommandInput(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendCommand(inputText)
                        inputText = ""
                    }
                },
                onCancel = { viewModel.cancelCurrentRequest() },
                isProcessing = assistantState == AssistantState.PROCESSING ||
                        assistantState == AssistantState.EXECUTING_ACTION ||
                        assistantState == AssistantState.CANCELLING,
                onVoiceClick = handleVoiceClick,
                isVoiceListening = speechState == SpeechState.LISTENING,
                isVoiceProcessing = speechState == SpeechState.PROCESSING || speechState == SpeechState.INITIALIZING,
                voiceSoundLevel = speechSoundLevel
            )
        }
    }
}

@Composable
fun LiveActionProgressBanner(
    progress: com.example.models.ActionProgressUpdate,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (progress.status == com.example.models.ActionStepStatus.STARTED) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else if (progress.status == com.example.models.ActionStepStatus.COMPLETED) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF2E7D32)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Step ${progress.currentStep} of ${progress.totalSteps}: ${progress.stepTitle}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = progress.detailMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("cancel_action_progress_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Task",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MyraOrbAvatar(state: AssistantState) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == AssistantState.PROCESSING) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val glowColor = when (state) {
        AssistantState.IDLE -> Color(0xFF8E24AA)
        AssistantState.PROCESSING -> Color(0xFF673AB7)
        AssistantState.EXECUTING_ACTION -> Color(0xFF00BFA5)
        AssistantState.BACKGROUND_READY -> Color(0xFF0288D1)
        AssistantState.CANCELLING, AssistantState.CANCELLED -> Color(0xFFFF9800)
        AssistantState.ERROR -> Color(0xFFE53935)
    }

    Box(
        modifier = Modifier
            .size(38.dp)
            .scale(if (state == AssistantState.PROCESSING) scale else 1f)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = 0.8f), Color(0xFF1E1035))
                )
            )
            .border(1.5.dp, glowColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = "Myra AI",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun AssistantStatusBadge(state: AssistantState) {
    val (bgColor, textColor, label) = when (state) {
        AssistantState.IDLE -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "Ready")
        AssistantState.PROCESSING -> Triple(Color(0xFFE8DEF8), Color(0xFF4A4458), "Thinking…")
        AssistantState.EXECUTING_ACTION -> Triple(Color(0xFFC8E6C9), Color(0xFF1B5E20), "Executing…")
        AssistantState.BACKGROUND_READY -> Triple(Color(0xFFB2EBF2), Color(0xFF006064), "Background")
        AssistantState.CANCELLING -> Triple(Color(0xFFFFECB3), Color(0xFF795548), "Cancelling…")
        AssistantState.CANCELLED -> Triple(Color(0xFFEEEEEE), Color(0xFF616161), "Cancelled")
        AssistantState.ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Error")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (state == AssistantState.PROCESSING || state == AssistantState.EXECUTING_ACTION || state == AssistantState.CANCELLING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = textColor
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun SuggestionChipsRow(
    onSelectChip: (String) -> Unit
) {
    val suggestions = listOf(
        "Tell me a joke",
        "YouTube खोलो और Free Fire search करो",
        "Fruit info apple",
        "Cat fact",
        "Bitcoin price",
        "YouTube खोलो",
        "Chrome खोलो",
        "Settings खोलो",
        "Google पर Free Fire search करो",
        "मुझसे हमेशा हिंदी में बात करना"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { chipText ->
            InputChip(
                selected = false,
                onClick = { onSelectChip(chipText) },
                label = { Text(chipText, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = when {
                            chipText.contains("YouTube") -> Icons.Default.PlayArrow
                            chipText.contains("Chrome") || chipText.contains("Google खोलो") -> Icons.Default.OpenInBrowser
                            chipText.contains("Settings") -> Icons.Default.Settings
                            chipText.contains("search") -> Icons.Default.Search
                            else -> Icons.Default.AutoAwesome
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = InputChipDefaults.inputChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.testTag("suggestion_chip_${chipText.take(10)}")
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isSpeaking: Boolean = false,
    onSpeak: ((String) -> Unit)? = null,
    onStopSpeaking: (() -> Unit)? = null
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Myra",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = containerColor),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )

                    // Action badge if this response executed an Android action
                    if (!isUser && message.actionType != null && message.actionType != "GENERAL_CHAT") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (message.actionSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (message.actionSuccess == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Action: ${message.actionType} ${if (message.actionTarget != null) "(${message.actionTarget})" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isUser) {
                            VoiceSpeakerIconButton(
                                isCurrentSpeaking = isSpeaking,
                                onSpeak = { onSpeak?.invoke(message.text) },
                                onStop = { onStopSpeaking?.invoke() }
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyConversationState(
    onSuggestionClick: (String) -> Unit,
    onOpenAppsSheet: () -> Unit,
    onOpenApiExplorer: () -> Unit = {},
    onStartVoice: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF673AB7), Color(0xFF1E1035))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "नमस्ते! मैं Myra हूँ",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Your Persistent Android AI Assistant",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Try these commands:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                QuickActionItem(
                    title = "🎙️ Voice Command (बोलकर आदेश दें)",
                    subtitle = "Hands-free Speech-to-Text: 'YouTube खोलो', 'Check Weather', or any task",
                    onClick = onStartVoice
                )

                QuickActionItem(
                    title = "🌐 Public APIs Catalog & Live Testing",
                    subtitle = "25+ curated no-auth APIs (Jokes, Nutrition, Weather, Trivia, Crypto)",
                    onClick = onOpenApiExplorer
                )

                QuickActionItem(
                    title = "📱 Installed Apps देखें & Launch करें",
                    subtitle = "Query device apps via PackageManager & launch with text commands",
                    onClick = onOpenAppsSheet
                )

                QuickActionItem(
                    title = "YouTube खोलो और Free Fire search करो",
                    subtitle = "V2 Multi-Step: Opens YouTube & searches Google sequentially",
                    onClick = { onSuggestionClick("YouTube खोलो और Free Fire search करो") }
                )

                QuickActionItem(
                    title = "YouTube खोलो",
                    subtitle = "Launches YouTube; Myra stays active in background",
                    onClick = { onSuggestionClick("YouTube खोलो") }
                )

                QuickActionItem(
                    title = "Google पर Free Fire search करो",
                    subtitle = "Executes structured Google search",
                    onClick = { onSuggestionClick("Google पर Free Fire search करो") }
                )

                QuickActionItem(
                    title = "Settings खोलो",
                    subtitle = "Opens Android Device Settings",
                    onClick = { onSuggestionClick("Settings खोलो") }
                )

                QuickActionItem(
                    title = "मुझसे हमेशा हिंदी में बात करना",
                    subtitle = "Saves language preference in local Room memory",
                    onClick = { onSuggestionClick("मुझसे हमेशा हिंदी में बात करना") }
                )
            }
        }
    }
}

@Composable
fun QuickActionItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Launch,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun BottomCommandInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    isProcessing: Boolean,
    onVoiceClick: () -> Unit = {},
    isVoiceListening: Boolean = false,
    isVoiceProcessing: Boolean = false,
    voiceSoundLevel: Float = 0f
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val handleSend = {
        if (text.isNotBlank() && !isProcessing) {
            focusManager.clearFocus()
            keyboardController?.hide()
            onSend()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = if (isProcessing) "Request running… (tap Cancel or type new command)" else "Type a command… (e.g. YouTube खोलो)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field"),
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { handleSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            // Voice Command Button (Hands-Free STT)
            VoiceMicButton(
                isListening = isVoiceListening,
                isProcessing = isVoiceProcessing,
                soundLevel = voiceSoundLevel,
                onClick = onVoiceClick
            )

            if (isProcessing) {
                FilledTonalButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("cancel_command_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Request",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                FilledIconButton(
                    onClick = handleSend,
                    enabled = text.isNotBlank(),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_command_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Command"
                    )
                }
            }
        }
    }
}
