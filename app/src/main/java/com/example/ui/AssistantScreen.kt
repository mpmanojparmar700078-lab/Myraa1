package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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

    var inputText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showInstalledAppsSheet by remember { mutableStateOf(false) }

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

    if (showSettingsDialog) {
        SettingsDialog(
            customApiKey = customApiKey,
            isServiceEnabled = serviceEnabled,
            isAccessibilityEnabled = isAccessibilityEnabled,
            preferences = preferences,
            onSaveApiKey = { viewModel.saveCustomApiKey(it) },
            onToggleService = { viewModel.setForegroundServiceEnabled(it) },
            onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
            onSetLanguage = { viewModel.updateLanguagePreference(it) },
            onDismiss = { showSettingsDialog = false }
        )
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Myra",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                AssistantStatusBadge(state = assistantState)
                            }
                            Text(
                                text = "Persistent AI Assistant Layer (V2)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.loadInstalledApps()
                            showInstalledAppsSheet = true
                        },
                        modifier = Modifier.testTag("installed_apps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Installed Apps"
                        )
                    }
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.testTag("clear_conversation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear Conversation"
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
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
                },
                onOpenAppsSheet = {
                    viewModel.loadInstalledApps()
                    showInstalledAppsSheet = true
                }
            )

            // Conversation Messages Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    EmptyConversationState(
                        onSuggestionClick = { viewModel.sendCommand(it) },
                        onOpenAppsSheet = {
                            viewModel.loadInstalledApps()
                            showInstalledAppsSheet = true
                        }
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
                            MessageBubble(message = msg)
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
                    LiveActionProgressBanner(progress = progress)
                }
            }

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
                isProcessing = assistantState == AssistantState.PROCESSING || assistantState == AssistantState.EXECUTING_ACTION
            )
        }
    }
}

@Composable
fun LiveActionProgressBanner(progress: com.example.models.ActionProgressUpdate) {
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
        AssistantState.ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Error")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (state == AssistantState.PROCESSING || state == AssistantState.EXECUTING_ACTION) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = textColor
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SuggestionChipsRow(
    onSelectChip: (String) -> Unit,
    onOpenAppsSheet: () -> Unit
) {
    val suggestions = listOf(
        "YouTube खोलो और Free Fire search करो",
        "YouTube खोलो",
        "Chrome खोलो",
        "Settings खोलो",
        "Google खोलो",
        "Google पर Free Fire search करो",
        "Open Chrome and search weather",
        "मुझसे हमेशा हिंदी में बात करना",
        "Always talk in English",
        "Hello Myra"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InputChip(
            selected = false,
            onClick = onOpenAppsSheet,
            label = { Text("📱 Installed Apps", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = InputChipDefaults.inputChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ),
            modifier = Modifier.testTag("suggestion_chip_apps_list")
        )

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
fun MessageBubble(message: MessageEntity) {
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

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyConversationState(
    onSuggestionClick: (String) -> Unit,
    onOpenAppsSheet: () -> Unit
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
    isProcessing: Boolean
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
                        text = "Type a command… (e.g. YouTube खोलो)",
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

            FilledIconButton(
                onClick = handleSend,
                enabled = text.isNotBlank() && !isProcessing,
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_command_button"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Command"
                    )
                }
            }
        }
    }
}
