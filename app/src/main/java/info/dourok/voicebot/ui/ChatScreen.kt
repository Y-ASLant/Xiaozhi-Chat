package info.dourok.voicebot.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewMode = hiltViewModel()
) {
    val messages by viewModel.display.chatFlow.collectAsState()
    val emotion by viewModel.display.emotionFlow.collectAsState()
    val deviceState by viewModel.deviceStateFlow.collectAsState() 
    var isListening by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val lazyListState = rememberLazyListState()
    
    // 计算状态栏高度
    val statusBarHeight = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    
    // 监听设备状态变化
    LaunchedEffect(deviceState) {
        isListening = deviceState == DeviceState.LISTENING || deviceState == DeviceState.SPEAKING
        Log.d("ChatScreen", "设备状态: $deviceState, 是否正在对话: $isListening")
    }
    
    // 监听按下和释放事件
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    // 仅在IDLE状态下按下按钮才开始识别
                    if (deviceState == DeviceState.IDLE) {
                        Log.d("ChatScreen", "按下按钮，开始语音识别")
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.startListening()
                    }
                }
                is PressInteraction.Release -> {
                    // 只有在LISTENING状态下松开才停止识别
                    if (deviceState == DeviceState.LISTENING) {
                        Log.d("ChatScreen", "松开按钮，停止语音识别")
                        viewModel.stopListening()
                    }
                }
                is PressInteraction.Cancel -> {
                    // 处理取消事件，类似于Release
                    if (deviceState == DeviceState.LISTENING) {
                        Log.d("ChatScreen", "取消按钮交互，停止语音识别")
                        viewModel.stopListening()
                    }
                }
            }
        }
    }
    
    // 每当新消息添加时滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.scrollToItem(0)
        }
    }
    
    // Emotion to emoji mapping
    val emotionEmojiMap = mapOf(
        "neutral" to "😐",
        "happy" to "😊",
        "laughing" to "😂",
        "funny" to "🤡",
        "sad" to "😢",
        "angry" to "😠",
        "crying" to "😭",
        "loving" to "🥰",
        "embarrassed" to "😳",
        "surprised" to "😮",
        "shocked" to "😱",
        "thinking" to "🤔",
        "winking" to "😉",
        "cool" to "😎",
        "relaxed" to "😌",
        "delicious" to "😋",
        "kissy" to "😘",
        "confident" to "😏",
        "sleepy" to "😴",
        "silly" to "🤪",
        "confused" to "😕"
    )
    
    // 用于呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp) // 为底部按钮留出空间
                .padding(top = statusBarHeight), // 为状态栏留出空间
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 头部状态区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 设备状态指示
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 状态指示点
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when (deviceState) {
                                        DeviceState.IDLE -> MaterialTheme.colorScheme.primary
                                        DeviceState.LISTENING -> MaterialTheme.colorScheme.tertiary
                                        DeviceState.SPEAKING -> MaterialTheme.colorScheme.secondary
                                        DeviceState.FATAL_ERROR -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                )
                                .scale(if (isListening) scale else 1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (deviceState) {
                                DeviceState.IDLE -> "空闲中"
                                DeviceState.LISTENING -> "正在聆听..."
                                DeviceState.SPEAKING -> "正在回复..."
                                DeviceState.CONNECTING -> "连接中..."
                                DeviceState.STARTING -> "启动中..."
                                DeviceState.FATAL_ERROR -> "出错了"
                                else -> deviceState.name.lowercase().replaceFirstChar { it.uppercase() }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // 情绪表情
                    Text(
                        text = emotionEmojiMap[emotion.lowercase()] ?: "😐",
                        style = TextStyle(fontSize = 32.sp),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            
            // 聊天消息列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = lazyListState,
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messages.reversed()) { message ->
                    ChatMessageItem(message)
                }
            }
        }
        
        // 底部麦克风按钮
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 主按钮
            FloatingActionButton(
                onClick = { 
                    // 添加点击事件处理，在Speaking状态下打断说话
                    if (deviceState == DeviceState.SPEAKING) {
                        Log.d("ChatScreen", "点击打断说话按钮")
                        viewModel.abortSpeaking(info.dourok.voicebot.protocol.AbortReason.NONE)
                    }
                },
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                containerColor = when (deviceState) {
                    DeviceState.SPEAKING -> MaterialTheme.colorScheme.error
                    DeviceState.LISTENING -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                interactionSource = interactionSource
            ) {
                Icon(
                    imageVector = if (deviceState == DeviceState.SPEAKING) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (deviceState == DeviceState.SPEAKING) "打断说话" else "按住说话",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            // 按钮下方的提示文字
            Text(
                text = when (deviceState) {
                    DeviceState.IDLE -> "按住说话"
                    DeviceState.LISTENING -> "松开结束"
                    DeviceState.SPEAKING -> "点击打断"
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun ChatMessageItem(message: Message) {
    val isCurrentUser = message.sender == "user"
    val bubbleShape = if (isCurrentUser) 
        RoundedCornerShape(18.dp)
    else 
        RoundedCornerShape(18.dp)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        // 发送者标识圆点
        if (!isCurrentUser) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
            )
        }
        
        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrentUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 280.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // 对于用户不显示sender，只为AI回复显示
                if (!isCurrentUser) {
                    Text(
                        text = if (message.sender == "assistant") "Assistant" else message.sender,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // 消息内容
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrentUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 时间戳
                Text(
                    text = message.nowInString.split(" ")[1], // 只显示时间部分
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentUser)
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.End)
                )
            }
        }
        
        // 用户标识圆点
        if (isCurrentUser) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, top = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
