package info.dourok.voicebot

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.ConnectivityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.dourok.voicebot.data.model.DeviceInfo
import info.dourok.voicebot.data.model.DummyDataGenerator
import info.dourok.voicebot.data.model.fromJsonToDeviceInfo
import info.dourok.voicebot.data.model.toJson
import javax.inject.Singleton
import androidx.core.content.edit
import dagger.hilt.EntryPoint
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Qualifier

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSharedPreferences(application: Application): SharedPreferences {
        return application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideApplicationContext(application: Application): Context {
        return application.applicationContext
    }

    @Provides
    @Singleton
    fun provideResources(application: Application): Resources {
        return application.resources
    }

    @Provides
    @Singleton
    fun provideConnectivityManager(application: Application): ConnectivityManager {
        return application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Provides
    @Singleton
    fun provideDeviceInfo(sp: SharedPreferences): DeviceInfo {
        sp.getString("device_id", null)?.let {
            println(it)
            return fromJsonToDeviceInfo(it)
        }?:run {
            val deviceInfo = DummyDataGenerator.generate()
            sp.edit { putString("device_id", deviceInfo.toJson()) }
            return deviceInfo
        }
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideCoroutineScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

    @DefaultDispatcher
    @Provides
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

}

@Module
@InstallIn(SingletonComponent::class)
object NavigationModule {
    @Provides
    @Singleton
    @NavigationEvents
    fun provideNavigationEvents(): MutableSharedFlow<String> {
        return MutableSharedFlow(extraBufferCapacity = 1)
    }
}

@EntryPoint
@InstallIn(ActivityComponent::class)
interface NavigationEntryPoint {
    @NavigationEvents
    fun getNavigationEvents(): MutableSharedFlow<String>
}

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class NavigationEvents


@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class DefaultDispatcher