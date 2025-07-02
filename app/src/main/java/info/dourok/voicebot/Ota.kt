package info.dourok.voicebot

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.sink
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import info.dourok.voicebot.data.model.Activation
import info.dourok.voicebot.data.model.DeviceInfo
import info.dourok.voicebot.data.model.MqttConfig
import info.dourok.voicebot.data.model.OtaResult
import info.dourok.voicebot.data.model.fromJsonToOtaResult
import info.dourok.voicebot.data.model.toJson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Ota @Inject constructor(private val context: Context,
                              val deviceInfo: DeviceInfo
) {
    companion object {
        private const val TAG = "Ota"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()


    var otaResult: OtaResult? = null

    val headers = mutableMapOf<String, String>()
    val currentVersion = deviceInfo.application.version
    val firmwareUrl: String
        get() = otaResult?.firmware?.url ?: ""
    val hasActivationCode: Boolean
        get() = otaResult?.activation != null


    // 升级进度和速度的 Flow
    private val _upgradeState = MutableStateFlow(Pair(0, 0L)) // (progress, speed)
    val upgradeState: StateFlow<Pair<Int, Long>> = _upgradeState

    // 设置 HTTP Header
    fun setHeader(key: String, value: String) {
        headers[key] = value
    }

    init {
        setHeader("Device-Id", deviceInfo.mac_address);
        setHeader("Client-Id", deviceInfo.uuid);
        setHeader("X-Language", "Chinese");
    }



    // 检查版本
    suspend fun checkVersion(checkVersionUrl: String,): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Current version: $currentVersion")

        if (checkVersionUrl.length < 10) {
            Log.e(TAG, "Check version URL is not properly set")
            return@withContext false
        }

        val requestBuilder = Request.Builder()
            .url(checkVersionUrl)
        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
        requestBuilder.addHeader("Content-Type", "application/json")
        val postDataJson = deviceInfo.toJson()
        val request = if (postDataJson.isNotEmpty()) {
            requestBuilder.post(
                postDataJson
                    .toRequestBody("application/json".toMediaTypeOrNull()!!)
            )
        } else {
            requestBuilder.get()
        }.build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to open HTTP connection: ${response.code}")
                return@withContext false
            }

            val responseBody = response.body?.string() ?: run {
                Log.e(TAG, "Empty response body")
                return@withContext false
            }
            Log.i(TAG, "Response: $responseBody")
            val json = JSONObject(responseBody)
            parseJsonResponse(json)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed: ${e.message}")
            return@withContext false
        }
    }

    // 解析 JSON 响应
    private fun parseJsonResponse(json: JSONObject) {
        // Activation
        otaResult = fromJsonToOtaResult(json)
    }

    // 标记当前版本有效（Android 不直接支持分区，这里模拟）
    suspend fun markCurrentVersionValid() {
        Log.i(TAG, "Marking current version as valid (Android simulation)")
        // Android 不需要分区管理，通常由系统验证 APK
    }

    // 升级固件
    suspend fun upgrade(firmwareUrl: String = this.firmwareUrl) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Upgrading firmware from $firmwareUrl")

        val request = Request.Builder()
            .url(firmwareUrl)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download firmware: ${response.code}")
                return@withContext
            }

            val contentLength = response.body?.contentLength() ?: 0L
            if (contentLength == 0L) {
                Log.e(TAG, "Failed to get content length")
                return@withContext
            }

            val file = File(context.cacheDir, "firmware.apk")
            val sink = file.sink().buffer()
            val source = response.body?.source() ?: return@withContext

            var totalRead = 0L
            var recentRead = 0L
            var lastCalcTime = System.currentTimeMillis()

            while (true) {
                val read = source.read(sink.buffer, 512)
                if (read == -1L) break

                recentRead += read
                totalRead += read
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCalcTime >= 1000 || read == 0L) {
                    val progress = (totalRead * 100 / contentLength).toInt()
                    val speed = recentRead * 1000 / (currentTime - lastCalcTime) // 字节/秒
                    Log.i(TAG, "Progress: $progress% ($totalRead/$contentLength), Speed: $speed B/s")
                    _upgradeState.emit(Pair(progress, speed))
                    lastCalcTime = currentTime
                    recentRead = 0L
                }
            }

            sink.close()
            source.close()

            // 验证和安装（Android APK 示例）
            val downloadedVersion = "1.0.0" // 假设从文件元数据获取，实际需解析
            if (downloadedVersion == currentVersion) {
                Log.e(TAG, "Firmware version is the same, skipping upgrade")
                return@withContext
            }

            installFirmware(file)
            Log.i(TAG, "Firmware upgrade successful, restarting app...")
            delay(3000) // 模拟重启
            restartApp()

        } catch (e: Exception) {
            Log.e(TAG, "Upgrade failed: ${e.message}")
        }
    }

    // 开始升级，带回调
    suspend fun startUpgrade() {
        upgrade(firmwareUrl)
    }

    // 解析版本号
    private fun parseVersion(version: String): List<Int> {
        return version.split(".").map { it.toInt() }
    }

    // 检查是否有新版本
    private fun isNewVersionAvailable(currentVersion: String, newVersion: String): Boolean {
        val current = parseVersion(currentVersion)
        val newer = parseVersion(newVersion)

        for (i in 0 until minOf(current.size, newer.size)) {
            if (newer[i] > current[i]) return true
            if (newer[i] < current[i]) return false
        }
        return newer.size > current.size
    }

    // 安装固件（Android APK 示例）
    private fun installFirmware(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
        }
        context.startActivity(intent)
    }

    // 重启应用
    private fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
