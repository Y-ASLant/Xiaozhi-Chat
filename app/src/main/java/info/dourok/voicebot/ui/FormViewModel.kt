package info.dourok.voicebot.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.dourok.voicebot.NavigationEvents
import info.dourok.voicebot.UiState
import info.dourok.voicebot.data.FormRepository
import info.dourok.voicebot.data.FormResult
import info.dourok.voicebot.data.SettingsRepository
import info.dourok.voicebot.data.model.ServerFormData
import info.dourok.voicebot.data.model.ServerType
import info.dourok.voicebot.data.model.ValidationResult
import info.dourok.voicebot.data.model.XiaoZhiConfig
import info.dourok.voicebot.domain.SubmitFormUseCase
import info.dourok.voicebot.domain.ValidateFormUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// :feature:form/ui/FormViewModel.kt
@HiltViewModel
class FormViewModel @Inject constructor(
    private val validateFormUseCase: ValidateFormUseCase,
    private val submitFormUseCase: SubmitFormUseCase,
    private val repository: FormRepository,
    private val settingsRepository: SettingsRepository,
    @NavigationEvents private val navigationEvents: MutableSharedFlow<String>
) : ViewModel() {
    private val _formState = MutableStateFlow(ServerFormData())
    val formState = _formState.asStateFlow()

    private val _validationResult = MutableStateFlow(ValidationResult(true))
    val validationResult = _validationResult.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.resultFlow.collect {
                when (it) {
                    is FormResult.XiaoZhiResult -> it.otaResult?.let {
                        if (it.activation != null) {
                            Log.d("FormViewModel", "activationFlow: $it")
                            navigationEvents.emit("activation")
                        } else {
                            navigationEvents.emit("chat")
                        }
                    }

                    null -> {} // ignore
                }
            }
        }
    }

    // 更新 XiaoZhi 配置
    fun updateXiaoZhiConfig(updater: XiaoZhiConfig) {
        _formState.update { it.copy(xiaoZhiConfig = updater) }
        validateForm()
    }

    private fun validateForm() {
        _validationResult.value = validateFormUseCase(_formState.value)
    }

    // 初始化从SettingsRepository获取skipConfigAfterConnect设置
    fun initializeSkipConfigSetting() {
        _formState.update { it.copy(skipConfigAfterConnect = settingsRepository.skipConfigAfterConnect) }
    }

    // 更新"绑定后跳过配置"设置
    fun updateSkipConfigSetting(skip: Boolean) {
        _formState.update { it.copy(skipConfigAfterConnect = skip) }
    }

    fun submitForm() {
        viewModelScope.launch {
            val validation = validateFormUseCase(_formState.value)
            _validationResult.value = validation

            if (validation.isValid) {
                _uiState.value = UiState.Loading
                val result = submitFormUseCase(_formState.value)
                
                // 保存是否跳过配置设置
                settingsRepository.skipConfigAfterConnect = _formState.value.skipConfigAfterConnect
                Log.d("FormViewModel", "保存skipConfigAfterConnect: ${_formState.value.skipConfigAfterConnect}")
                
                _uiState.value = if (result.isSuccess) {
                    UiState.Success("保存成功")
                } else {
                    UiState.Error("保存失败")
                }
            }
        }
    }
}