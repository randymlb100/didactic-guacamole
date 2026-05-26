package com.lotterynet.pro.core.update

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.ui.login.LoginActivity
import com.lotterynet.pro.ui.update.UpdatePromptActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UpdateManager(
    private val application: Application,
    private val repository: UpdateRepository = UpdateRepository(application),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sessions = LocalSessionRepository(application)

    @Volatile
    private var checkRunning = false

    fun register() {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) {
                    checkFromForeground(activity)
                }
            },
        )
    }

    fun checkFromForeground(activity: Activity) {
        if (activity is LoginActivity || activity is UpdatePromptActivity) return
        if (UpdatePromptActivity.isShowing) return
        if (checkRunning) return
        val session = sessions.getActiveSession() ?: return
        checkRunning = true
        scope.launch {
            val result = repository.checkForUpdate(activity.packageName, session)
            checkRunning = false
            val info = when (result) {
                is OtaCheckResult.Success -> result.info.takeIf { it.shouldInstall }
                is OtaCheckResult.Offline -> result.cachedInfo?.takeIf { it.shouldInstall && it.blocksCurrentBuild }
                is OtaCheckResult.Error -> result.cachedInfo?.takeIf { it.shouldInstall && it.blocksCurrentBuild }
            } ?: return@launch
            if (!activity.isFinishing && !activity.isDestroyed && !UpdatePromptActivity.isShowing) {
                activity.startActivity(
                    Intent(activity, UpdatePromptActivity::class.java).apply {
                        putExtra(UpdatePromptActivity.EXTRA_UPDATE_JSON, info.toJson().toString())
                    },
                )
            }
        }
    }
}
