package info.dourok.voicebot

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import info.dourok.voicebot.data.SettingsRepository
import info.dourok.voicebot.ui.ActivationScreen
import info.dourok.voicebot.ui.ChatScreen
import info.dourok.voicebot.ui.ServerFormScreen
import info.dourok.voicebot.ui.theme.VoicebotclientandroidTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        (getSystemService(AUDIO_SERVICE) as AudioManager).mode = AudioManager.MODE_IN_COMMUNICATION
        Log.d("MainActivity", "onCreate")
        
        // 设置沉浸式状态栏
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 输出设置信息以便调试
        val skipConfig = settingsRepository.skipConfigAfterConnect
        val webSocketUrl = settingsRepository.webSocketUrl
        Log.d("MainActivity", "skipConfigAfterConnect: $skipConfig, webSocketUrl: $webSocketUrl")
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                0
            )
        } else {
            Log.d("MainActivity", "Permission granted")
        }
        enableEdgeToEdge()
        setContent {
            VoicebotclientandroidTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentColor = MaterialTheme.colorScheme.background,
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ) { innerPadding ->
                    AppNavigation(
                        paddingValues = innerPadding,
                        skipConfig = settingsRepository.skipConfigAfterConnect && settingsRepository.webSocketUrl != null
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    skipConfig: Boolean = false
) {
    val navController = rememberNavController()
    val activity = LocalContext.current as Activity
    val entryPoint = EntryPointAccessors.fromActivity(activity, NavigationEntryPoint::class.java)
    val navigationEvents = entryPoint.getNavigationEvents()

    Log.d("AppNavigation", "navigationEvents: $navigationEvents, skipConfig: $skipConfig")
    
    val startDestination = if (skipConfig) "chat" else "form"
    Log.d("AppNavigation", "起始页面: $startDestination")

    LaunchedEffect(navController) {
        navigationEvents.collect { route ->
            Log.d("AppNavigation", "导航到: $route")
            navController.navigate(route)
        }
    }

    Column(modifier = Modifier.padding(paddingValues)) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable("form") { ServerFormScreen() }
            composable("activation") { ActivationScreen() }
            composable("chat") { ChatScreen() }
        }
    }
}

