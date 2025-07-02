package info.dourok.voicebot.data

import info.dourok.voicebot.ApplicationScope
import info.dourok.voicebot.Ota
import info.dourok.voicebot.data.model.OtaResult
import info.dourok.voicebot.data.model.ServerFormData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

sealed class FormResult{
    class XiaoZhiResult(val otaResult: OtaResult?) : FormResult()
}

// :feature:form/data/FormRepository.kt
interface FormRepository {
    suspend fun submitForm(formData: ServerFormData)

    val resultFlow : StateFlow<FormResult?>
}




@Singleton
class FormRepositoryImpl @Inject constructor(
    private val ota: Ota,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val coroutineScope: CoroutineScope,
) : FormRepository {

    override suspend fun submitForm(formData: ServerFormData) {
        settingsRepository.transportType = formData.xiaoZhiConfig.transportType
        settingsRepository.webSocketUrl = formData.xiaoZhiConfig.webSocketUrl
        Log.d("FormRepository", "提交表单: transportType=${formData.xiaoZhiConfig.transportType}, webSocketUrl=${formData.xiaoZhiConfig.webSocketUrl}")
        
        ota.checkVersion(formData.xiaoZhiConfig.qtaUrl)
        resultFlow.value = FormResult.XiaoZhiResult(ota.otaResult)
        settingsRepository.mqttConfig = ota.otaResult?.mqttConfig
        
        print(ota.deviceInfo)
    }

    override val resultFlow: MutableStateFlow<FormResult?> = MutableStateFlow(null)
}
