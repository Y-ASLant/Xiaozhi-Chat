package info.dourok.voicebot.domain

import info.dourok.voicebot.data.FormRepository
import info.dourok.voicebot.data.model.ServerFormData
import javax.inject.Inject

// :feature:form/domain/SubmitFormUseCase.kt
class SubmitFormUseCase @Inject constructor(
    private val repository: FormRepository
) {
    suspend operator fun invoke(formData: ServerFormData): Result<Unit> {
        return try {
            repository.submitForm(formData)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}