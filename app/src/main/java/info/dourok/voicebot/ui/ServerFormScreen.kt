package info.dourok.voicebot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.dourok.voicebot.R
import info.dourok.voicebot.UiState
import info.dourok.voicebot.data.model.ServerType
import info.dourok.voicebot.data.model.TransportType
import info.dourok.voicebot.data.model.XiaoZhiConfig

@Composable
fun ServerFormScreen(
    viewModel: FormViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    // 初始化从SettingsRepository获取skipConfigAfterConnect设置
    LaunchedEffect(Unit) {
        viewModel.initializeSkipConfigSetting()
    }

    // 计算状态栏高度
    val statusBarHeight = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarHeight + 16.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Chat服务器配置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                XiaoZhiConfigSection(
                    xiaoZhiConfig = formState.xiaoZhiConfig,
                    errors = validationResult.errors,
                    onXiaoZhiUpdate = viewModel::updateXiaoZhiConfig
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 添加自动跳过设置选项
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = formState.skipConfigAfterConnect,
                onCheckedChange = { viewModel.updateSkipConfigSetting(it) }
            )
            Text(
                text = "绑定设备后不再显示该界面",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Button(
            onClick = { viewModel.submitForm() },
            enabled = uiState !is UiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) { 
            Text(
                stringResource(R.string.label_conn),
                style = MaterialTheme.typography.titleMedium
            ) 
        }

        when (val state = uiState) {
            is UiState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    Text("正在连接...", style = MaterialTheme.typography.bodyLarge)
                }
            }
            is UiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        state.message, 
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            is UiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        state.message, 
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            is UiState.Idle -> {}
        }
    }
}

// XiaoZhi服务器配置部分
@Composable
fun XiaoZhiConfigSection(
    xiaoZhiConfig: XiaoZhiConfig,
    errors: Map<String, String>,
    onXiaoZhiUpdate: (XiaoZhiConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = xiaoZhiConfig.webSocketUrl,
            onValueChange = { onXiaoZhiUpdate(xiaoZhiConfig.copy(webSocketUrl = it)) },
            label = { Text("WebSocket URL") },
            isError = errors.containsKey("xiaoZhiWebSocketUrl"),
            supportingText = { errors["xiaoZhiWebSocketUrl"]?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
        
        OutlinedTextField(
            value = xiaoZhiConfig.qtaUrl,
            onValueChange = { onXiaoZhiUpdate(xiaoZhiConfig.copy(qtaUrl = it)) },
            label = { Text("QTA URL") },
            isError = errors.containsKey("qtaUrl"),
            supportingText = { errors["qtaUrl"]?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
        
        Text(
            "传输类型", 
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TransportType.entries.forEach { type ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = xiaoZhiConfig.transportType == type,
                        onClick = { onXiaoZhiUpdate(xiaoZhiConfig.copy(transportType = type)) }
                    )
                    Text(
                        when(type) {
                            TransportType.MQTT -> "MQTT"
                            TransportType.WebSockets -> "WebSockets"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}