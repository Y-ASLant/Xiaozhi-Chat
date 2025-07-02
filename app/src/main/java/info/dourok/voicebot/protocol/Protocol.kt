package info.dourok.voicebot.protocol

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// 模拟 Protocol 基类中的枚举和常量
enum class AbortReason { WAKE_WORD_DETECTED, NONE }
enum class ListeningMode { ALWAYS_ON, AUTO_STOP, MANUAL }
enum class AudioState { OPENED, CLOSED }

// Protocol 抽象类（对应 C++ 的 Protocol）
abstract class Protocol {
    protected var sessionId: String = "" // 会话 ID
    protected val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 回调改为 Flow
    val incomingJsonFlow = MutableSharedFlow<JSONObject>()
    val incomingAudioFlow = MutableSharedFlow<ByteArray>()
    val audioChannelStateFlow = MutableSharedFlow<AudioState>()
    val networkErrorFlow = MutableSharedFlow<String>()

    abstract suspend fun start()
    abstract suspend fun sendAudio(data: ByteArray)
    abstract suspend fun openAudioChannel(): Boolean
    abstract fun closeAudioChannel()
    abstract fun isAudioChannelOpened(): Boolean
    abstract suspend fun sendText(text: String)

    suspend fun sendAbortSpeaking(reason: AbortReason) {
        val json = JSONObject().apply {
            put("session_id", sessionId)
            put("type", "abort")
            if (reason == AbortReason.WAKE_WORD_DETECTED) put("reason", "wake_word_detected")
        }
        sendText(json.toString())
    }

    suspend fun sendWakeWordDetected(wakeWord: String) {
        val json = JSONObject().apply {
            put("session_id", sessionId)
            put("type", "listen")
            put("state", "detect")
            put("text", wakeWord)
        }
        sendText(json.toString())
    }

    suspend fun sendStartListening(mode: ListeningMode) {
        val json = JSONObject().apply {
            put("session_id", sessionId)
            put("type", "listen")
            put("state", "start")
            put("mode", when (mode) {
                ListeningMode.ALWAYS_ON -> "realtime"
                ListeningMode.AUTO_STOP -> "auto"
                ListeningMode.MANUAL -> "manual"
            })
        }
        sendText(json.toString())
    }

    suspend fun sendStopListening() {
        val json = JSONObject().apply {
            put("session_id", sessionId)
            put("type", "listen")
            put("state", "stop")
        }
        sendText(json.toString())
    }

    suspend fun sendIotDescriptors(descriptors: String) {
        val json = JSONObject().apply {
            put("session_id", sessionId)
            put("type", "iot")
            put("descriptors", JSONObject(descriptors))
        }
        sendText(json.toString())
    }

    suspend fun sendIotStates(states: String) {
        val json = JSONObject().apply {
            put("session_id", sessionId)
            put("type", "iot")
            put("states", JSONObject(states))
        }
        sendText(json.toString())
    }

    abstract fun dispose()
}

