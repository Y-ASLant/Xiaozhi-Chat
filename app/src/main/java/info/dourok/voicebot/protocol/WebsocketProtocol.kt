package info.dourok.voicebot.protocol
import android.util.Log
import info.dourok.voicebot.data.model.DeviceInfo
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// WebsocketProtocol 实现
class WebsocketProtocol(private val deviceInfo: DeviceInfo,
                        private val url: String,
                        private val accessToken: String) : Protocol() {
    companion object {
        private const val TAG = "WS"
        private const val OPUS_FRAME_DURATION_MS = 60
    }

    private var isOpen: Boolean = false
    private var websocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val helloReceived = CompletableDeferred<Boolean>()

    init {
        sessionId = "your_session_id" // 模拟 session_id，实际从系统获取
    }

    override suspend fun start() {
        // 空实现，与 C++ 一致
    }

    override suspend fun sendAudio(data: ByteArray) {
        // Log.i(TAG, "Sending audio: ${data.size}")
        websocket?.run {
            send(ByteString.of(*data))
        } ?: Log.e(TAG, "WebSocket is null")
    }

    override suspend fun sendText(text: String) {
        Log.i(TAG, "Sending text: $text")
        websocket?.run {
            send(text)
        } ?: Log.e(TAG, "WebSocket is null")
    }

    override fun isAudioChannelOpened(): Boolean {
        return websocket != null && isOpen
    }

    override fun closeAudioChannel() {
        websocket?.close(1000, "Normal closure")
        websocket = null
    }

    override suspend fun openAudioChannel(): Boolean = withContext(Dispatchers.IO) {
        // 关闭旧连接
        closeAudioChannel()

        // 创建 WebSocket 请求
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Protocol-Version", "1")
            .addHeader("Device-Id", deviceInfo.mac_address) //
            .addHeader("Client-Id", deviceInfo.uuid) //
            .build()
        Log.i(TAG, "WebSocket connecting to $url")
        // Log header
        request.headers.forEach { (name, value) ->
            Log.i(TAG, "Header: $name: $value")
        }

        // 初始化 WebSocket
        websocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isOpen = true
                Log.i(TAG, "WebSocket connected")
                scope.launch {
                    audioChannelStateFlow.emit(AudioState.OPENED)
                }

                // 发送 Hello 消息
                val helloMessage = JSONObject().apply {
                    put("type", "hello")
                    put("version", 1)
                    put("transport", "websocket")
                    put("audio_params", JSONObject().apply {
                        put("format", "opus")
                        put("sample_rate", 16000)
                        put("channels", 1)
                        put("frame_duration", OPUS_FRAME_DURATION_MS)
                    })
                }
                Log.i(TAG, "WebSocket hello: $helloMessage")
                webSocket.send(helloMessage.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i(TAG, "WebSocket message: $text")
                scope.launch {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    when (type) {
                        "hello" -> parseServerHello(json)
                        else -> incomingJsonFlow.emit(json)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Log.i(TAG, "WebSocket binary message: ${bytes.size}")
                scope.launch {
                    incomingAudioFlow.emit(bytes.toByteArray())
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code: $reason")
                super.onClosing(webSocket, code, reason)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isOpen = false;
                Log.i(TAG, "WebSocket closed: $code: $reason")
                scope.launch {
                    audioChannelStateFlow.emit(AudioState.CLOSED)
                }
                websocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isOpen = false;
                t.printStackTrace()
                Log.e(TAG, "WebSocket error: ${t.message}")
                scope.launch {
                    networkErrorFlow.emit("Server not found")
                }
                websocket = null
            }
        })
        // 防止client在连接建立后立即销毁
        // client.dispatcher.executorService.shutdown()

        // 等待服务器 Hello（模拟 C++ 的 xEventGroupWaitBits）
        try {
            withTimeout(10000) {
                Log.i(TAG, "Waiting for server hello")
                helloReceived.await()
                Log.i(TAG, "Server hello received")
                true
            }
        } catch (_: TimeoutCancellationException) {
            Log.e(TAG, "Failed to receive server hello")
            networkErrorFlow.emit("Server timeout")
            closeAudioChannel()
            false
        }
    }

    private fun parseServerHello(root: JSONObject) {
        val transport = root.optString("transport")
        if (transport != "websocket") {
            Log.e(TAG, "Unsupported transport: $transport")
            return
        }

        val audioParams = root.optJSONObject("audio_params")
        audioParams?.let {
            val sampleRate = it.optInt("sample_rate", -1)
            if (sampleRate != -1) {
                serverSampleRate = sampleRate
            }
        }
        sessionId = root.optString("session_id")


        helloReceived.complete(true)
    }

    // 清理资源
    override fun dispose() {
        scope.cancel()
        closeAudioChannel()
        client.dispatcher.executorService.shutdown()
    }

    private var serverSampleRate: Int = -1 // 模拟 C++ 的成员变量
}