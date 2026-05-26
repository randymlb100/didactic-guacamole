package com.lotterynet.pro

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter
import com.lotterynet.pro.core.update.UpdateManager
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

class LotteryNetApp : Application() {
    @Volatile
    private var currentActivityName: String? = null
    private lateinit var updateManager: UpdateManager

    override fun onCreate() {
        super.onCreate()
        runCatching { bootstrapSentry() }
            .onFailure { error ->
                Log.e("LotteryNetSentry", "Sentry bootstrap failed", error)
                NativeCrashReporter(this).recordHandled("LotteryNetApp.bootstrapSentry", error)
            }
        runCatching { registerSafeLifecycleCallbacks() }
            .onFailure { error ->
                Log.e("LotteryNetApp", "Lifecycle callback registration failed", error)
                NativeCrashReporter(this).recordHandled("LotteryNetApp.lifecycleCallbacks", error)
            }
        runCatching {
            updateManager = UpdateManager(this)
            updateManager.register()
        }.onFailure { error ->
            Log.e("LotteryNetApp", "OTA bootstrap failed", error)
            NativeCrashReporter(this).recordHandled("LotteryNetApp.ota", error)
        }
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            NativeCrashReporter(this).recordUnhandled(
                activityName = currentActivityName,
                threadName = thread.name,
                throwable = throwable,
            )
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun registerSafeLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) {
                    currentActivityName = activity::class.java.simpleName
                    runCatching {
                        Sentry.configureScope { scope ->
                            scope.setTag("current_activity", currentActivityName.orEmpty())
                        }
                    }.onFailure {
                        Log.w("LotteryNetSentry", "Failed to tag current activity", it)
                    }
                }

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    if (currentActivityName == activity::class.java.simpleName) {
                        currentActivityName = null
                    }
                }
            },
        )
    }

    private fun bootstrapSentry() {
        val dsn = resolveSentryDsn()
        if (dsn.isBlank()) {
            Log.i("LotteryNetSentry", "Sentry disabled: missing DSN")
            return
        }
        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            if (options.environment.isNullOrBlank()) {
                options.environment = BuildConfig.SENTRY_ENVIRONMENT
            }
            options.isDebug = BuildConfig.DEBUG
            options.setEnableAutoSessionTracking(true)
            if (options.tracesSampleRate == null) {
                options.tracesSampleRate = 0.2
            }
            options.setAttachScreenshot(false)
            options.setSendDefaultPii(false)
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                event.setTag("app_layer", "android-native")
                event.setTag("build_variant", if (BuildConfig.DEBUG) "debug" else "release")
                if (event.level == SentryLevel.DEBUG) null else event
            }
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
                breadcrumb.setData("app_layer", "android-native")
                breadcrumb
            }
        }
        Sentry.configureScope { scope ->
            scope.setTag("app_layer", "android-native")
        }
        Log.i("LotteryNetSentry", "Sentry enabled for environment=${BuildConfig.SENTRY_ENVIRONMENT}")
    }

    private fun resolveSentryDsn(): String {
        val buildConfigDsn = BuildConfig.SENTRY_DSN.trim()
        if (buildConfigDsn.isNotBlank()) {
            return buildConfigDsn
        }
        val manifestDsn = runCatching {
            packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
                .metaData
                ?.getString("io.sentry.dsn")
                ?.trim()
                .orEmpty()
        }.getOrDefault("")
        if (manifestDsn.isNotBlank()) {
            return manifestDsn
        }
        return ""
    }
}
