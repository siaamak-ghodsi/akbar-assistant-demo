package com.akbar.assistant.demo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akbar.assistant.demo.AppLanguage
import com.akbar.assistant.demo.AssistantState
import com.akbar.assistant.demo.AssistantUiState
import com.akbar.assistant.demo.ChatMessage
import com.akbar.assistant.demo.commands.AssistantCommand

private val Bg = Color(0xFFF4F6F8)
private val CardBg = Color(0xFFFFFFFF)
private val Ink = Color(0xFF1E293B)
private val Muted = Color(0xFF64748B)
private val Line = Color(0xFFE2E8F0)
private val Accent = Color(0xFF334155)
private val Soft = Color(0xFF94A3B8)
private val Teal = Color(0xFF0F766E)
private val UserBubble = Color(0xFF1E293B)
private val BotBubble = Color(0xFFEEF2F6)

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
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("اکبر", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text("دستیار صوتی", color = Muted, fontSize = 13.sp)
            }
            val listening = state.state == AssistantState.LISTENING_WAKE ||
                state.state == AssistantState.LISTENING_COMMAND
            Surface(
                color = if (state.sessionActive) Color(0xFFD1FAE5) else Line,
                shape = RoundedCornerShape(999.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    listening -> Teal
                                    state.sessionActive -> Color(0xFF059669)
                                    else -> Soft
                                },
                            ),
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = when {
                            state.sessionActive && listening -> "در حال گوش دادن"
                            state.sessionActive -> "جلسه فعال"
                            else -> "بگو اکبر"
                        },
                        color = if (state.sessionActive) Color(0xFF065F46) else Ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = state.statusText,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
            )
            if (state.hintText.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(state.hintText, color = Muted, fontSize = 12.sp)
            }
            AnimatedVisibility(
                visible = state.lastHeard.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = "شنیدم: ${state.lastHeard}",
                    color = Soft,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (!state.permissionGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRequestMicPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("اجازه میکروفون")
                }
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = CardBg,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 1.dp,
        ) {
            if (state.chatMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.sessionActive) {
                            "دستور بگو یا بنویس\nمثلاً: ساعت چند است؟"
                        } else {
                            "برای شروع بگو «اکبر»\nیا «هی اکبر»"
                        },
                        color = Muted,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp,
                        fontSize = 15.sp,
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
                placeholder = { Text("پیام بنویس…", color = Soft) },
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
                    disabledBorderColor = Line,
                    disabledContainerColor = Color(0xFFF8FAFC),
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
                    disabledContentColor = Soft,
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text("ارسال", fontWeight = FontWeight.SemiBold)
            }
        }

        if (state.testModeVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("ابزار تست", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSimulateWake(AppLanguage.PERSIAN) },
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("اکبر") }
                    TextButton(onClick = { onTestCommand(AssistantCommand.TellTime(AppLanguage.PERSIAN)) }) {
                        Text("ساعت")
                    }
                    TextButton(onClick = { onTestCommand(AssistantCommand.Weather(AppLanguage.PERSIAN)) }) {
                        Text("هوا")
                    }
                    TextButton(onClick = { onTestCommand(AssistantCommand.LightOn(AppLanguage.PERSIAN)) }) {
                        Text("چراغ")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("حالت تست", color = Muted, fontSize = 13.sp)
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
