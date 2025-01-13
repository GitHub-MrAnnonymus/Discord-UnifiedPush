package to.us.charlesst.discord

import android.app.Activity
import android.app.Application
import android.os.Bundle

class AppLifecycleTracker private constructor() : Application.ActivityLifecycleCallbacks {
    private var activityReferences = 0
    private var isActivityChangingConfigurations = false

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    
    override fun onActivityStarted(activity: Activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            isAppInForeground = true
        }
    }

    override fun onActivityResumed(activity: Activity) {}
    
    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            isAppInForeground = false
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        @Volatile
        var isAppInForeground = false
            private set

        private var instance: AppLifecycleTracker? = null

        fun init(application: Application) {
            if (instance == null) {
                instance = AppLifecycleTracker()
                application.registerActivityLifecycleCallbacks(instance)
            }
        }
    }
}