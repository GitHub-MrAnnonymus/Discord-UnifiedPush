package to.us.charlesst.discord

import android.app.Application

class DiscordApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLifecycleTracker.init(this)
    }
}