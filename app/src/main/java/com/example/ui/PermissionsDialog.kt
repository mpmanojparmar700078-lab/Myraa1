package com.example.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.platform.android.PermissionManager
import com.example.ui.theme.Emerald500

@Composable
fun PermissionsDialog(
    isAccessibilityEnabled: Boolean,
    isMicrophoneGranted: Boolean,
    isNotificationGranted: Boolean,
    isBatteryOptimized: Boolean,
    onRequestMicrophone: () -> Unit,
    onRequestNotification: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessibilityNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("अनुमतियाँ और सिस्टम सेवाएँ", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Myra को पूर्ण रूप से कार्य करने के लिए निम्नलिखित अनुमतियों की आवश्यकता है:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Accessibility Service
                PermissionItemCard(
                    icon = Icons.Default.AccessibilityNew,
                    title = "एक्सेसिबिलिटी सर्विस (Accessibility)",
                    description = "स्क्रीन पढ़ने, बैक जाने, होम स्क्रीन, स्क्रॉल और ऐप्स में बटन क्लिक करने के लिए आवश्यक है।",
                    isGranted = isAccessibilityEnabled,
                    statusText = if (isAccessibilityEnabled) "सक्रिय है (Active)" else "बंद है (Disabled)",
                    actionButtonText = if (isAccessibilityEnabled) "सेटिंग्स फिर से देखें" else "Accessibility सेटिंग्स खोलें",
                    onAction = { PermissionManager.openAccessibilitySettings(context) },
                    guideSteps = listOf(
                        "1. नीचे बटन दबाएं और Android Settings में जाएं",
                        "2. 'Downloaded Apps' या 'Installed Apps' में Myra खोजें",
                        "3. 'Myra Accessibility Service' को ON करें"
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Microphone Permission
                PermissionItemCard(
                    icon = Icons.Default.Mic,
                    title = "माइक्रोफोन (Microphone)",
                    description = "बोलकर कमांड देने और वॉयस इनपुट के लिए आवश्यक है।",
                    isGranted = isMicrophoneGranted,
                    statusText = if (isMicrophoneGranted) "अनुमति प्राप्त है (Granted)" else "अनुमति नहीं है (Missing)",
                    actionButtonText = if (isMicrophoneGranted) "ऐप सेटिंग्स" else "माइक अनुमति दें (Allow)",
                    onAction = {
                        if (!isMicrophoneGranted) {
                            onRequestMicrophone()
                        } else {
                            PermissionManager.openAppSettings(context)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Notification Permission
                PermissionItemCard(
                    icon = Icons.Default.Notifications,
                    title = "सूचनाएं (Notifications)",
                    description = "बैकग्राउंड में Myra को सक्रिय रखने और अलर्ट्स के लिए आवश्यक है।",
                    isGranted = isNotificationGranted,
                    statusText = if (isNotificationGranted) "अनुमति प्राप्त है (Granted)" else "अनुमति नहीं है (Missing)",
                    actionButtonText = if (isNotificationGranted) "नोटिफिकेशन सेटिंग्स" else "अनुमति दें (Allow)",
                    onAction = {
                        if (!isNotificationGranted) {
                            onRequestNotification()
                        } else {
                            PermissionManager.openNotificationSettings(context)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Battery Optimization
                PermissionItemCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "बैटरी ऑप्टिमाइज़ेशन (Background Run)",
                    description = "ताकि सिस्टम Myra को बैकग्राउंड में अचानक बंद न करे।",
                    isGranted = isBatteryOptimized,
                    statusText = if (isBatteryOptimized) "अनुकूलित (Unrestricted)" else "सिस्टम द्वारा सीमित हो सकता है",
                    actionButtonText = "बैटरी प्रतिबंध हटाएं",
                    onAction = { PermissionManager.requestIgnoreBatteryOptimization(context) }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("पूर्ण (Done)")
            }
        }
    )
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    statusText: String,
    actionButtonText: String,
    onAction: () -> Unit,
    guideSteps: List<String>? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) Emerald500 else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (isGranted) Emerald500 else MaterialTheme.colorScheme.error, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) Emerald500 else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isGranted) Emerald500 else MaterialTheme.colorScheme.error
                )
            }

            if (guideSteps != null && !isGranted) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    guideSteps.forEach { step ->
                        Text(
                            text = step,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (!isGranted) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionButtonText)
                }
            } else {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionButtonText, fontSize = 12.sp)
                }
            }
        }
    }
}
