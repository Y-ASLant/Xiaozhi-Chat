package info.dourok.voicebot.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindFormRepository(
        formRepositoryImpl: FormRepositoryImpl
    ): FormRepository

    @Binds
    abstract fun bindSettingsRepository(
        formRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository
}

