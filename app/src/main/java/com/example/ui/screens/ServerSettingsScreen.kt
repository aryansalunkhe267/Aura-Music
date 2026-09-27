package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.StreamingApiClient
import com.example.data.SettingsManager
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ServerSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeUrl by settingsManager.streamingInstanceUrl.collectAsStateWithLifecycle()

    var inputUrl by remember(activeUrl) { mutableStateOf(activeUrl) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var testIsSuccessful by remember { mutableStateOf<Boolean?>(null) }

    fun testAndSave(urlToTest: String) {
        val sanitized = if (!urlToTest.endsWith("/")) "$urlToTest/" else urlToTest
        isTestingConnection = true
        testStatusMessage = "Testing instance connectivity..."
        testIsSuccessful = null

        coroutineScope.launch {
            try {
                StreamingApiClient.customBaseUrl = sanitized
                val response = withContext(Dispatchers.IO) {
                    StreamingApiClient.api.searchSongs("lofi", filter = "music_songs")
                }
                if (response.isSuccessful && response.body() != null) {
                    settingsManager.setStreamingInstanceUrl(sanitized)
                    testIsSuccessful = true
                    testStatusMessage = "Connected successfully to instance!"
                    Toast.makeText(context, "Active streaming instance updated", Toast.LENGTH_SHORT).show()
                } else {
                    testIsSuccessful = false
                    testStatusMessage = "Instance responded with HTTP ${response.code()}"
                }
            } catch (e: Exception) {
                testIsSuccessful = false
                testStatusMessage = "Connection failed: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isTestingConnection = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneUIDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp)
            .testTag("server_settings_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OneUITextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Streaming Provider Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITextPrimary
                )
                Text(
                    text = "Configure open-source Piped/Invidious instance",
                    fontSize = 12.sp,
                    color = OneUITextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Active Instance Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Active API Base URL",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneUITextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = activeUrl,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "All music searches, streaming metadata, and audio stream extraction route through this endpoint without requiring API keys.",
                    fontSize = 12.sp,
                    color = OneUITextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom URL Input
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Custom Instance URL",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    placeholder = { Text("https://your-instance.com/", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OneUISurfaceDark,
                        unfocusedContainerColor = OneUISurfaceDark,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = OneUITextPrimary,
                        unfocusedTextColor = OneUITextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("custom_instance_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { testAndSave(inputUrl.trim()) },
                        enabled = !isTestingConnection && inputUrl.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("save_instance_btn")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Test & Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val defaultUrl = StreamingApiClient.DEFAULT_INSTANCES.first()
                            inputUrl = defaultUrl
                            testAndSave(defaultUrl)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OneUISurfaceDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = "Default", tint = OneUITextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Default", color = OneUITextPrimary)
                    }
                }

                // Status feedback
                testStatusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = when (testIsSuccessful) {
                            true -> MaterialTheme.colorScheme.primary
                            false -> Color.Red
                            else -> OneUITextSecondary
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Known Public Instances List
        Text(
            text = "PRESET PUBLIC INSTANCES",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = OneUITextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )

        StreamingApiClient.DEFAULT_INSTANCES.forEach { presetUrl ->
            val isSelected = activeUrl == presetUrl
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) OneUISurfaceDark else OneUICardElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable {
                        inputUrl = presetUrl
                        testAndSave(presetUrl)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = presetUrl,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else OneUITextPrimary
                        )
                        Text(
                            text = if (presetUrl.contains("kavin")) "Official primary instance" else "Public mirror node",
                            fontSize = 11.sp,
                            color = OneUITextSecondary
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
