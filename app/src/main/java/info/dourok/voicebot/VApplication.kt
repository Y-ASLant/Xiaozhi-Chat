package info.dourok.voicebot

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}