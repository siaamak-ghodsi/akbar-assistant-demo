package com.akbar.assistant.demo.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akbar.assistant.demo.AppLanguage
import com.akbar.assistant.demo.AssistantState
import com.akbar.assistant.demo.AssistantUiState
import com.akbar.assistant.demo.commands.AssistantCommand
import kotlin.math.cos
import kotlin.math.sin

private val Bg = Color(0xFFF3F5F7)
private val CardBg = Color(0xFFFFFFFF)
private val Ink = Color(0xFF1E293B)
private val Muted = Color(0xFF94A3B8)
private val Line = Color(0xFFE2E8F0)
private val Accent = Color(0xFF334155)
private val UserBubble = Color(0xFF1E293B)
private val BotBubble = Color(0xFFEEF2F6)
private val ListenBlue = Color(0xFF38BDF8)
private val SpeakAmber = Color(0xFFFBBF24)
private val IdleRing = Color(0xFFCBD5E1)

@Composable
fun AkbarAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            background = Bg,
            surface = CardBg,
            onPrimary = Color.White,
            onBackground = Ink,
            onSurface = Ink,
        ),
        content = content,
    )
}

@Composable
fun AssistantScreen(
    state: AssistantUiState,
    onToggleTestMode: () -> Unit,
    onSimulateWake: (AppLanguage) -> Unit,
    onTestCommand: (AssistantCommand) -> Unit,
    onDraftChanged: (String) -> Unit,
    onSendText: () -> Unit,
    onRequestMicPermission: () -> Unit = {},
    onInstallPersianTts: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.lastIndex)
        }
    }

    val listening = state.state == AssistantState.LISTENING_WAKE ||
        state.state == AssistantState.LISTENING_COMMAND
    val speaking = state.state == AssistantState.SPEAKING
    val processing = state.state == AssistantState.PROCESSING ||
        state.state == AssistantState.ACTIVATED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8FAFC), Bg, Color(0xFFE8EEF4)),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Brand title for employer demo
        Text(
            text = "intel tech",
            color = Ink.copy(alpha = 0.94f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        )

        if (!state.permissionGranted) {
            Button(
                onClick = onRequestMicPermission,
                modifier = Modifier.padding(horizontal = 22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("\u0627\u062c\u0627\u0632\u0647 \u0645\u06cc\u06a9\u0631\u0648\u0641\u0648\u0646")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (state.needsPersianTtsInstall) {
            Button(
                onClick = onInstallPersianTts,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("\u0646\u0635\u0628 \u0635\u062f\u0627\u06cc \u0641\u0627\u0631\u0633\u06cc (Google TTS)")
            }
            Text(
                text = "\u0628\u062f\u0648\u0646 \u0628\u0633\u062a\u0647\u0654 \u0632\u0628\u0627\u0646 \u0641\u0627\u0631\u0633\u06cc\u060c \u067e\u0627\u0633\u062e\u200c\u0647\u0627 \u0634\u0646\u06cc\u062f\u0647 \u0646\u0645\u06cc\u200c\u0634\u0648\u0646\u062f",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )
        }

        // Guardian orb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            GuardianOrb(
                listening = listening,
                speaking = speaking,
                processing = processing,
                rms = state.rmsLevel,
            )
        }

        // Live status + what the mic just heard (wake feedback)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.statusText.isNotBlank()) {
                Text(
                    text = state.statusText,
                    color = Ink.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
            if (state.lastHeard.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\u00ab${state.lastHeard}\u00bb",
                    color = if (listening) ListenBlue else Accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp,
                )
            } else if (listening) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (state.state == AssistantState.LISTENING_WAKE) {
                        "\u062f\u0631 \u0627\u0646\u062a\u0638\u0627\u0631 \u0647\u06cc \u0627\u06a9\u0628\u0631\u2026"
                    } else {
                        "\u062f\u0631 \u062d\u0627\u0644 \u0634\u0646\u06cc\u062f\u0646\u2026"
                    },
                    color = Muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (state.hintText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.hintText,
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            state.errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = err,
                    color = Color(0xFFDC2626),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chat history (answers stay visible; orb shows voice state)
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = CardBg.copy(alpha = 0.92f),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 1.dp,
        ) {
            if (state.chatMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\u0628\u06af\u0648 \u0647\u06cc \u0627\u06a9\u0628\u0631 \u062a\u0627 \u0634\u0631\u0648\u0639 \u06a9\u0646\u06cc\u0645",
                        color = Muted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.chatMessages, key = { it.id }) { message ->
                        val user = message.fromUser
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
                        ) {
                            Surface(
                                color = if (user) UserBubble else BotBubble,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (user) 16.dp else 4.dp,
                                    bottomEnd = if (user) 4.dp else 16.dp,
                                ),
                            ) {
                                Text(
                                    text = message.text,
                                    color = if (user) Color.White else Ink,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.draftText,
                onValueChange = onDraftChanged,
                modifier = Modifier.weight(1f),
                enabled = state.permissionGranted,
                placeholder = { Text("", color = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Line,
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    cursorColor = Accent,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendText() }),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSendText,
                enabled = state.permissionGranted && state.draftText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.White,
                    disabledContainerColor = Line,
                    disabledContentColor = Muted,
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "\u0627\u0631\u0633\u0627\u0644",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (state.testModeVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onSimulateWake(AppLanguage.PERSIAN) },
                    shape = RoundedCornerShape(10.dp),
                ) { Text("\u0627\u06a9\u0628\u0631") }
                OutlinedButton(
                    onClick = { onSimulateWake(AppLanguage.ENGLISH) },
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Hey") }
                TextButton(onClick = { onTestCommand(AssistantCommand.TellTime(AppLanguage.PERSIAN)) }) {
                    Text("\u0633\u0627\u0639\u062a")
                }
                TextButton(onClick = { onTestCommand(AssistantCommand.TellTime(AppLanguage.ENGLISH)) }) {
                    Text("Time")
                }
                TextButton(onClick = { onTestCommand(AssistantCommand.Weather(AppLanguage.PERSIAN)) }) {
                    Text("\u0647\u0648\u0627")
                }
                TextButton(onClick = { onTestCommand(AssistantCommand.LightOn(AppLanguage.ENGLISH)) }) {
                    Text("Light")
                }
                TextButton(onClick = { onTestCommand(AssistantCommand.CameraOn(AppLanguage.PERSIAN)) }) {
                    Text("\u062f\u0648\u0631\u0628\u06cc\u0646")
                }
                TextButton(onClick = { onTestCommand(AssistantCommand.GmailOpen(AppLanguage.PERSIAN)) }) {
                    Text("\u062c\u06cc\u0645\u06cc\u0644")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Switch(
                checked = state.testModeVisible,
                onCheckedChange = { onToggleTestMode() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Line,
                ),
            )
        }
    }
}

@Composable
private fun GuardianOrb(
    listening: Boolean,
    speaking: Boolean,
    processing: Boolean,
    rms: Float,
) {
    val infinite = rememberInfiniteTransition(label = "guardian")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )
    val energy by animateFloatAsState(
        targetValue = when {
            speaking -> 0.9f
            listening -> 0.45f + rms.coerceIn(0f, 1f) * 0.45f
            processing -> 0.55f
            else -> 0.22f
        },
        animationSpec = tween(350),
        label = "energy",
    )
    val core = when {
        speaking -> SpeakAmber
        listening -> ListenBlue
        processing -> Color(0xFF60A5FA)
        else -> IdleRing
    }

    Canvas(modifier = Modifier.size(220.dp)) {
        val min = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val scale = if (listening || speaking || processing) pulse else 1f

        // Soft glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(core.copy(alpha = 0.22f * energy), Color.Transparent),
                center = center,
                radius = min * 0.48f * scale,
            ),
            radius = min * 0.48f * scale,
            center = center,
        )

        // Outer ring
        drawCircle(
            color = core.copy(alpha = 0.28f + energy * 0.35f),
            radius = min * 0.34f * scale,
            center = center,
            style = Stroke(width = (2.4f + energy * 3.2f).dp.toPx()),
        )

        // Mid ring
        drawCircle(
            color = core.copy(alpha = 0.45f),
            radius = min * 0.24f * scale,
            center = center,
            style = Stroke(width = 1.6.dp.toPx()),
        )

        // Core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f * energy),
                    core.copy(alpha = 0.35f + energy * 0.25f),
                    Color.Transparent,
                ),
                center = center,
                radius = min * (0.14f + energy * 0.06f),
            ),
            radius = min * (0.14f + energy * 0.06f),
            center = center,
        )

        // Orbiting speck while active
        if (listening || speaking || processing) {
            val rad = Math.toRadians(spin.toDouble())
            val orbit = min * 0.30f * scale
            val cx = center.x + (orbit * cos(rad)).toFloat()
            val cy = center.y + (orbit * sin(rad)).toFloat()
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = 2.4.dp.toPx(),
                center = Offset(cx, cy),
            )
        }
    }
}
