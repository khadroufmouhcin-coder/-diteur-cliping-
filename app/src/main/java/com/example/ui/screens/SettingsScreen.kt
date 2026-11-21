package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.GeminiSettings
import com.example.ui.theme.DeckContainer
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakRed
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.ViralYellow
import com.example.viewmodel.SettingsUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSaveAndVerify: (apiKey: String, modelName: String) -> Unit,
    onSaveOnly: (apiKey: String, modelName: String) -> Unit,
    onClearApiKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var apiKeyDraft by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var modelDraft by remember(state.modelName) { mutableStateOf(state.modelName) }
    var revealKey by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = StudioSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = ViralYellow
                            )
                            Text(
                                text = "Gemini REST API Key",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Stored locally on your device using Jetpack DataStore. Requests are sent directly to generativelanguage.googleapis.com over HTTPS.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate
                        )

                        OutlinedTextField(
                            value = apiKeyDraft,
                            onValueChange = { apiKeyDraft = it },
                            label = { Text("Gemini API Key (AIza...)") },
                            placeholder = { Text("Paste your Google AI Studio API key") },
                            singleLine = true,
                            visualTransformation = if (revealKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { revealKey = !revealKey },
                                    modifier = Modifier.testTag("toggle_api_key_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (revealKey) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                        contentDescription = if (revealKey) "Hide API key" else "Show API key",
                                        tint = MutedSlate
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricViolet,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_input")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Gemini Model",
                                style = MaterialTheme.typography.labelLarge,
                                color = MutedSlate
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GeminiSettings.PRESET_MODELS.forEach { preset ->
                                    val selected = modelDraft == preset
                                    Surface(
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable { modelDraft = preset }
                                            .testTag("model_chip_$preset"),
                                        shape = MaterialTheme.shapes.small,
                                        color = if (selected) ElectricViolet.copy(alpha = 0.24f) else DeckContainer,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) ElectricViolet else HairlineBorder
                                        )
                                    ) {
                                        Text(
                                            text = preset,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) Color.White else MutedSlate,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = modelDraft,
                                onValueChange = { modelDraft = it },
                                label = { Text("Model ID") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricViolet,
                                    unfocusedBorderColor = HairlineBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gemini_model_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSaveOnly(apiKeyDraft, modelDraft) },
                                enabled = !state.isVerifying,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_api_key_button")
                            ) {
                                Text("Save")
                            }

                            Button(
                                onClick = { onSaveAndVerify(apiKeyDraft, modelDraft) },
                                enabled = !state.isVerifying,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ViralYellow,
                                    contentColor = ObsidianBg
                                ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_verify_api_key_button")
                            ) {
                                if (state.isVerifying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = ObsidianBg,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Testing...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save & Test Key", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (state.apiKey.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    apiKeyDraft = ""
                                    onClearApiKey()
                                },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_api_key_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    tint = PeakRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear Stored Key", color = PeakRed)
                            }
                        }

                        state.statusMessage?.let { msg ->
                            val borderColor = if (state.isError) PeakRed else MintSuccess
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = DeckContainer,
                                border = BorderStroke(1.dp, borderColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.isError) {
                                            Icons.Filled.ErrorOutline
                                        } else {
                                            Icons.Filled.CheckCircle
                                        },
                                        contentDescription = null,
                                        tint = borderColor
                                    )
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = borderColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = StudioSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = NeonCyan
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Standalone APK Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "No FirebaseApp or google-services.json required. Audio RMS peak analysis, Media3 9:16 video rendering, YouTube live chat polling, and Gemini REST API calls all work standalone.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSlate
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
