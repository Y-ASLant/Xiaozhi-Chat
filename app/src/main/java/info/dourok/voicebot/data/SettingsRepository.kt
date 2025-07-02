package info.dourok.voicebot.data

import android.content.SharedPreferences
import androidx.core.content.edit
import info.dourok.voicebot.data.model.MqttConfig
import info.dourok.voicebot.data.model.TransportType
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    var transportType: TransportType
    var mqttConfig: MqttConfig?
    var webSocketUrl: String?
    var skipConfigAfterConnect: Boolean
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : SettingsRepository {
    companion object {
        private const val KEY_TRANSPORT_TYPE = "transport_type"
        private const val KEY_WEBSOCKET_URL = "websocket_url"
        private const val KEY_SKIP_CONFIG = "skip_config_after_connect"
        // MqttConfig 由应用程序其他部分管理，这里不进行持久化
    }

    override var transportType: TransportType
        get() = TransportType.valueOf(
            sharedPreferences.getString(KEY_TRANSPORT_TYPE, TransportType.WebSockets.name) ?: TransportType.WebSockets.name
        )
        set(value) {
            sharedPreferences.edit {
                putString(KEY_TRANSPORT_TYPE, value.name)
            }
        }

    override var mqttConfig: MqttConfig? = null

    override var webSocketUrl: String?
        get() = sharedPreferences.getString(KEY_WEBSOCKET_URL, null)
        set(value) {
            sharedPreferences.edit {
                if (value != null) {
                    putString(KEY_WEBSOCKET_URL, value)
                } else {
                    remove(KEY_WEBSOCKET_URL)
                }
            }
        }

    override var skipConfigAfterConnect: Boolean
        get() = sharedPreferences.getBoolean(KEY_SKIP_CONFIG, true)
        set(value) {
            sharedPreferences.edit {
                putBoolean(KEY_SKIP_CONFIG, value)
            }
        }
}