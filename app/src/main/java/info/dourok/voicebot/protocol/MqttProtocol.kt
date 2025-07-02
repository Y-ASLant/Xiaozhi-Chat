package info.dourok.voicebot.protocol

import android.content.Context
import android.util.Log
import info.dourok.voicebot.data.model.MqttConfig
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MqttProtocol(
    private val context: Context,
    private val mqttConfig: MqttConfig
) : Protocol() {
    private val TAG = "MqttProtocol"
    private var mqttClient: MqttAndroidClient? = null
    private var udpClient: UdpClient? = null
    private val channelMutex = Any()

    private var endpoint: String = "tcp://${mqttConfig.endpoint}"
    private var clientId: String = mqttConfig.clientId
    private var username: String = mqttConfig.username
    private var password: String = mqttConfig.password
    private var publishTopic: String = mqttConfig.publishTopic

    private lateinit var aesKey: SecretKeySpec
    private var aesNonce: ByteArray = ByteArray(16)
    private var localSequence: Long = 0
    private var remoteSequence: Long = 0

    override suspend fun start() {
        startMqttClient()
    }

    private suspend fun startMqttClient() = withContext(Dispatchers.IO) {
        if (mqttClient?.isConnected == true) {
            Log.w(TAG, "MQTT client already started")
            mqttClient?.disconnect()
        }

        if (endpoint.isEmpty()) {
            Log.e(TAG, "MQTT endpoint is not specified")
            networkErrorFlow.emit("Server not found")
            return@withContext
        }

        mqttClient = MqttAndroidClient(context, endpoint, clientId).apply {
            setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    Log.i(TAG, "Connected to endpoint")
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.i(TAG, "Disconnected from endpoint")
                    scope.launch { networkErrorFlow.emit("Connection lost") }
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    scope.launch {
                        val payload = String(message.payload)
                        val json = JSONObject(payload)
                        when (json.optString("type")) {
                            "hello" -> parseServerHello(json)
                            "goodbye" -> {
                                val sid = json.optString("session_id")
                                if (sid.isEmpty() || sid == sessionId) {
                                    closeAudioChannel()
                                }
                            }
                            else -> incomingJsonFlow.emit(json)
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
        }

        val options = MqttConnectOptions().apply {
            keepAliveInterval = 90
            isAutomaticReconnect = true
            userName = username
            password = this@MqttProtocol.password.toCharArray()
        }

        try {
            mqttClient?.connect(options)?.waitForCompletion()
            Log.i(TAG, "Connecting to endpoint $endpoint")
        } catch (e: MqttException) {
            Log.e(TAG, "Failed to connect to endpoint", e)
            networkErrorFlow.emit("Server not connected")
        }
    }

    override suspend fun sendAudio(data: ByteArray) {
        synchronized(channelMutex) {
            if (udpClient == null) return

            val nonce = aesNonce.copyOf().apply {
                val size = data.size.toShort()
                this[2] = (size.toInt() shr 8).toByte()
                this[3] = size.toByte()
                val seq = (++localSequence).toInt()
                this[12] = (seq shr 24).toByte()
                this[13] = (seq shr 16).toByte()
                this[14] = (seq shr 8).toByte()
                this[15] = seq.toByte()
            }

            val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, aesKey, javax.crypto.spec.IvParameterSpec(nonce))
            }
            val encrypted = cipher.doFinal(data)
            val packet = nonce + encrypted
            udpClient?.send(packet)
        }
    }

    override suspend fun openAudioChannel(): Boolean = withContext(Dispatchers.IO) {
        if (mqttClient?.isConnected != true) {
            Log.i(TAG, "MQTT is not connected, trying to connect now")
            startMqttClient()
            if (mqttClient?.isConnected != true) return@withContext false
        }

        sessionId = ""
        val helloMessage = JSONObject().apply {
            put("type", "hello")
            put("version", 3)
            put("transport", "udp")
            put("audio_params", JSONObject().apply {
                put("format", "opus")
                put("sample_rate", 16000)
                put("channels", 1)
                put("frame_duration", 20)
            })
        }
        sendText(helloMessage.toString())

        suspendCoroutine { cont ->
            scope.launch {
                delay(10000)
                if (sessionId.isEmpty()) {
                    Log.e(TAG, "Failed to receive server hello")
                    networkErrorFlow.emit("Server timeout")
                    cont.resume(false)
                } else {
                    cont.resume(true)
                }
            }
        }
    }

    override fun closeAudioChannel() {
        synchronized(channelMutex) {
            udpClient?.close()
            udpClient = null
        }
        scope.launch {
            val goodbyeMessage = JSONObject().apply {
                put("session_id", sessionId)
                put("type", "goodbye")
            }
            sendText(goodbyeMessage.toString())
            audioChannelStateFlow.emit(AudioState.CLOSED)
        }
    }

    override fun isAudioChannelOpened(): Boolean = udpClient != null

    override suspend fun sendText(text: String) {
        if (publishTopic.isEmpty() || mqttClient?.isConnected != true) return
        try {
            mqttClient?.publish(publishTopic, text.toByteArray(), 1, false)
        } catch (e: MqttException) {
            Log.e(TAG, "Failed to publish message", e)
            networkErrorFlow.emit("Server error")
        }
    }

    private suspend fun parseServerHello(json: JSONObject) {
        if (json.optString("transport") != "udp") {
            Log.e(TAG, "Unsupported transport: ${json.optString("transport")}")
            networkErrorFlow.emit("Unsupported transport")
            return
        }

        sessionId = json.optString("session_id")
        Log.i(TAG, "Session ID: $sessionId")

        val udp = json.optJSONObject("udp") ?: run {
            networkErrorFlow.emit("UDP not specified")
            return
        }
        val server = udp.optString("server")
        val port = udp.optInt("port")
        val key = decodeHexString(udp.optString("key"))
        aesNonce = decodeHexString(udp.optString("nonce"))
        aesKey = SecretKeySpec(key, "AES")

        synchronized(channelMutex) {
            udpClient?.close()
            udpClient = UdpClient(server, port).apply {
                setOnMessage { data ->
                    scope.launch {
                        if (data.size < aesNonce.size) {
                            Log.e(TAG, "Invalid audio packet size: ${data.size}")
                            return@launch
                        }
                        if (data[0].toInt() != 1) {
                            Log.e(TAG, "Invalid audio packet type: ${data[0]}")
                            return@launch
                        }
                        val sequence = ((data[12].toInt() shl 24) or
                                (data[13].toInt() shl 16) or
                                (data[14].toInt() shl 8) or
                                data[15].toInt()).toLong()
                        if (sequence < remoteSequence) {
                            Log.w(TAG, "Old sequence: $sequence, expected: $remoteSequence")
                            return@launch
                        }
                        if (sequence != remoteSequence + 1) {
                            Log.w(TAG, "Wrong sequence: $sequence, expected: ${remoteSequence + 1}")
                        }

                        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
                            init(Cipher.DECRYPT_MODE, aesKey, javax.crypto.spec.IvParameterSpec(data.copyOfRange(0, aesNonce.size)))
                        }
                        val decrypted = cipher.doFinal(data, aesNonce.size, data.size - aesNonce.size)
                        incomingAudioFlow.emit(decrypted)
                        remoteSequence = sequence
                    }
                }
            }
        }
        audioChannelStateFlow.emit(AudioState.OPENED)
    }

    private fun decodeHexString(hexString: String): ByteArray {
        return hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    override fun dispose() {
        scope.cancel()
        mqttClient?.disconnect()
        udpClient?.close()
    }
}

class UdpClient(
    private val server: String,
    private val port: Int
) {
    private val TAG = "UdpClient"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null
    private var serverAddress: InetAddress? = null
    private var isRunning = false
    private var onMessage: ((ByteArray) -> Unit)? = null

    init {
        try {
            serverAddress = InetAddress.getByName(server)
            socket = DatagramSocket().apply {
                soTimeout = 0 // Non-blocking mode
            }
            isRunning = true
            startReceiving()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize UDP client", e)
            close()
        }
    }

    fun send(data: ByteArray) {
        if (!isRunning || socket == null || serverAddress == null) {
            Log.w(TAG, "UDP client is not running or not initialized")
            return
        }

        scope.launch {
            try {
                val packet = DatagramPacket(data, data.size, serverAddress, port)
                socket?.send(packet)
                Log.d(TAG, "Sent UDP packet: ${data.size} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send UDP packet", e)
            }
        }
    }

    private fun startReceiving() {
        scope.launch {
            while (isRunning && socket != null) {
                try {
                    val buffer = ByteArray(65535) // Max UDP packet size
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    val receivedData = packet.data.copyOf(packet.length)
                    Log.d(TAG, "Received UDP packet: ${receivedData.size} bytes")
                    onMessage?.invoke(receivedData)
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Error receiving UDP packet", e)
                    }
                    delay(100) // Prevent tight loop on error
                }
            }
        }
    }

    fun setOnMessage(callback: (ByteArray) -> Unit) {
        onMessage = callback
    }

    fun close() {
        isRunning = false
        scope.cancel()
        socket?.close()
        socket = null
        Log.i(TAG, "UDP client closed")
    }
}

