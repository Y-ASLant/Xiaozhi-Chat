package info.dourok.voicebot.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import info.dourok.voicebot.AudioRecorder
import info.dourok.voicebot.OpusDecoder
import info.dourok.voicebot.OpusEncoder
import info.dourok.voicebot.OpusStreamPlayer
import info.dourok.voicebot.data.SettingsRepository
import info.dourok.voicebot.data.model.DeviceInfo
import info.dourok.voicebot.data.model.TransportType
import info.dourok.voicebot.protocol.AbortReason
import info.dourok.voicebot.protocol.ListeningMode
import info.dourok.voicebot.protocol.MqttProtocol
import info.dourok.voicebot.protocol.Protocol
import info.dourok.voicebot.protocol.WebsocketProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ChatViewMode @Inject constructor(
    @ApplicationContext private val context: Context,
    deviceInfo: DeviceInfo,
    settings: SettingsRepository
) : ViewModel() {
    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val protocol: Protocol = when (settings.transportType) {

        TransportType.MQTT -> {
            MqttProtocol(context, settings.mqttConfig!!)
        }

        TransportType.WebSockets -> {
            WebsocketProtocol(deviceInfo, settings.webSocketUrl!!, "test-token")
        }
    }

    val display = Display()
    var encoder: OpusEncoder? = null
    var decoder: OpusDecoder? = null
    var recorder: AudioRecorder? = null
    var player: OpusStreamPlayer? = null
    var aborted: Boolean = false
    var keepListening: Boolean = true
    val deviceStateFlow = MutableStateFlow(DeviceState.IDLE)
    var deviceState: DeviceState
        get() = deviceStateFlow.value
        set(value) {
            deviceStateFlow.value = value
        }

    init {

        deviceState = DeviceState.STARTING

        viewModelScope.launch {
            //FIXME start before checking the version
            protocol.start()
            deviceState = DeviceState.CONNECTING
            if (protocol.openAudioChannel()) {
                protocol.sendStartListening(ListeningMode.AUTO_STOP)
                withContext(Dispatchers.IO) {
                    launch {
                        val sampleRate = 24000
                        val channels = 1
                        val frameSizeMs = 60
                        player = OpusStreamPlayer(sampleRate, channels, frameSizeMs)
                        decoder = OpusDecoder(sampleRate, channels, frameSizeMs)
                        player?.start(protocol.incomingAudioFlow.map {
                            deviceState = DeviceState.SPEAKING
                            decoder?.decode(it)
                        })
                    }
                }
            } else {
                Log.e("WS", "Failed to open audio channel")
            }
            delay(1000)
            var i = 0
            // dummy opus audio bytearray
            launch {
                val sampleRate = 16000
                val channels = 1
                val frameSizeMs = 60
                encoder = OpusEncoder(sampleRate, channels, frameSizeMs)
                recorder = AudioRecorder(sampleRate, channels, frameSizeMs)
                val audioFlow = recorder?.startRecording()
                val opusFlow = audioFlow?.map { encoder?.encode(it) }
                deviceState = DeviceState.IDLE
                opusFlow?.collect {
                    it?.let { protocol.sendAudio(it) }
                }
            }

            launch {
                protocol.incomingJsonFlow.collect { json ->
                    val type = json.optString("type")
                    when (type) {
                        "tts" -> {
                            val state = json.optString("state")
                            when (state) {
                                "start" -> {
                                    schedule {
                                        aborted = false
                                        if (deviceState == DeviceState.IDLE || deviceState == DeviceState.LISTENING) {
                                            deviceState = DeviceState.SPEAKING
                                        }
                                    }
                                }

                                "stop" -> {
                                    schedule {
                                        if (deviceState == DeviceState.SPEAKING) {
                                            Log.i(TAG, "waiting for TTS to stop")
                                            player?.waitForPlaybackCompletion()
                                            Log.i(TAG, "TTS stopped")
                                            if (keepListening) {
                                                protocol.sendStartListening(ListeningMode.AUTO_STOP)
                                                deviceState = DeviceState.LISTENING
                                            } else {
                                                deviceState = DeviceState.IDLE
                                            }
                                        }
                                    }
                                }

                                "sentence_start" -> {
                                    val text = json.optString("text")
                                    if (text.isNotEmpty()) {
                                        Log.i(TAG, "<< $text")
                                        schedule {
                                            display.setChatMessage("assistant", text)
                                        }
                                    }
                                }
                            }
                        }

                        "stt" -> {
                            val text = json.optString("text")
                            if (text.isNotEmpty()) {
                                Log.i(TAG, ">> $text")
                                schedule {
                                    display.setChatMessage("user", text)
                                }
                            }
                        }

                        "llm" -> {
                            val emotion = json.optString("emotion")
                            if (emotion.isNotEmpty()) {
                                schedule {
                                    display.setEmotion(emotion)
                                }
                            }
                        }

                        "iot" -> {
                            val commands = json.optJSONArray("commands")
                            Log.i(TAG, "IOT commands: $commands")
//                            if (commands != null) {
//                                val thingManager = iot.ThingManager.getInstance()
//                                for (i in 0 until commands.length()) {
//                                    val command = commands.getJSONObject(i)
//                                    thingManager.invoke(command)
//                                }
//                            }
                        }
                    }
                }
            }
        }
    }

    fun startListening() {
        viewModelScope.launch {
            if (deviceState == DeviceState.ACTIVATING) {
                reboot()
                return@launch
            }

            keepListening = false
            if (deviceState == DeviceState.IDLE) {
                if (!protocol.isAudioChannelOpened()) {
                    deviceState = DeviceState.CONNECTING
                    if (!protocol.openAudioChannel()) {
                        deviceState = DeviceState.IDLE
                        return@launch
                    }
                }
                protocol.sendStartListening(ListeningMode.MANUAL)
                deviceState = DeviceState.LISTENING
            } else if (deviceState == DeviceState.SPEAKING) {
                abortSpeaking(AbortReason.NONE)
                protocol.sendStartListening(ListeningMode.MANUAL)
                delay(120) // Wait for the speaker to empty the buffer
                deviceState = DeviceState.LISTENING
            }
        }
    }

    private fun reboot() {
        // Implement the reboot logic here
    }

    fun abortSpeaking(reason: AbortReason) {
        Log.i(TAG, "Abort speaking")
        aborted = true
        viewModelScope.launch {
            protocol.sendAbortSpeaking(reason)
        }
    }
    private fun schedule(task: suspend () -> Unit) {
        viewModelScope.launch {
            task()
        }
    }


    fun stopListening() {
        viewModelScope.launch {
            if (deviceState == DeviceState.LISTENING) {
                protocol.sendStopListening()
                deviceState = DeviceState.IDLE
            }
        }
    }

    override fun onCleared() {
        protocol.dispose()
        encoder?.release()
        decoder?.release()
        player?.stop()
        recorder?.stopRecording()
        super.onCleared()
    }
}

enum class DeviceState {
    STARTING,
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ACTIVATING,
    FATAL_ERROR
}


class Display {
    val chatFlow = MutableStateFlow<List<Message>>(listOf())
    val emotionFlow = MutableStateFlow<String>("neutral")
    fun setChatMessage(sender: String, message: String) {
        chatFlow.value = chatFlow.value + Message(sender, message)
    }

    fun setEmotion(emotion: String) {
        emotionFlow.value = emotion
    }
}

@SuppressLint("ConstantLocale")
val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

data class Message(
    val sender: String = "",
    val message: String = "",
    val nowInString: String = df.format(System.currentTimeMillis())
)
