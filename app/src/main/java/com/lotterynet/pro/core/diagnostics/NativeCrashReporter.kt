package com.lotterynet.pro.core.diagnostics

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import io.sentry.Sentry
import io.sentry.SentryLevel
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

data class NativeCrashReport(
    val timestampMs: Long,
    val activityName: String?,
    val source: String?,
    val threadName: String?,
    val errorClass: String,
    val message: String?,
    val stackTrace: String,
) {
    fun toUserMessage(): String {
        val where = activityName ?: source ?: "pantalla nativa"
        val detail = message?.takeIf { it.isNotBlank() } ?: errorClass
        return "La app se cerro en $where. Detalle: $detail"
    }

    fun isUnhandledCrash(): Boolean = source == "uncaught"
}

class NativeCrashReporter(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordUnhandled(
        activityName: String?,
        threadName: String?,
        throwable: Throwable,
    ) {
        writeReport(
            activityName = activityName,
            source = "uncaught",
            threadName = threadName,
            throwable = throwable,
            markPending = true,
        )
    }

    fun recordHandled(
        source: String,
        throwable: Throwable,
    ) {
        writeReport(
            activityName = null,
            source = source,
            threadName = Thread.currentThread().name,
            throwable = throwable,
            markPending = false,
        )
    }

    fun consumePending(): NativeCrashReport? {
        val raw = prefs.getString(KEY_REPORT, null) ?: return null
        prefs.edit { remove(KEY_REPORT) }
        return parse(raw)?.takeIf { it.isUnhandledCrash() }
    }

    fun peekLatest(): NativeCrashReport? {
        val latestRaw = runCatching {
            latestReportFile().takeIf { it.exists() }?.readText()
        }.getOrNull()
        return parse(latestRaw ?: prefs.getString(KEY_LATEST_REPORT, null) ?: return null)
    }

    private fun writeReport(
        activityName: String?,
        source: String?,
        threadName: String?,
        throwable: Throwable,
        markPending: Boolean,
    ) {
        val stack = StringWriter().also { writer ->
            throwable.printStackTrace(PrintWriter(writer))
        }.toString()
        val report = JSONObject().apply {
            put("timestampMs", System.currentTimeMillis())
            put("activityName", activityName)
            put("source", source)
            put("threadName", threadName)
            put("errorClass", throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name })
            put("message", throwable.message)
            put("stackTrace", stack)
        }.toString()
        prefs.edit {
            if (markPending) {
                putString(KEY_REPORT, report)
            } else {
                remove(KEY_REPORT)
            }
            putString(KEY_LATEST_REPORT, report)
        }
        runCatching {
            latestReportFile().writeText(report)
        }
        runCatching {
            Sentry.withScope { scope ->
                activityName?.let { scope.setTag("activity_name", it) }
                source?.let { scope.setTag("native_source", it) }
                threadName?.let { scope.setTag("thread_name", it) }
                scope.level = if (source == "uncaught") SentryLevel.FATAL else SentryLevel.ERROR
                scope.setExtra("native_report_json", report)
                Sentry.captureException(throwable)
            }
        }.onFailure {
            Log.w(TAG, "Sentry capture failed for native report", it)
        }
        Log.e(TAG, "Native crash recorded. source=$source activity=$activityName thread=$threadName", throwable)
    }

    private fun latestReportFile(): File = File(appContext.filesDir, LATEST_REPORT_FILE)

    private fun parse(raw: String): NativeCrashReport? {
        return runCatching {
            val json = JSONObject(raw)
            NativeCrashReport(
                timestampMs = json.optLong("timestampMs", 0L),
                activityName = json.optString("activityName").takeIf { it.isNotBlank() },
                source = json.optString("source").takeIf { it.isNotBlank() },
                threadName = json.optString("threadName").takeIf { it.isNotBlank() },
                errorClass = json.optString("errorClass").ifBlank { "UnknownError" },
                message = json.optString("message").takeIf { it.isNotBlank() },
                stackTrace = json.optString("stackTrace"),
            )
        }.getOrNull()
    }

    companion object {
        private const val TAG = "LotteryNetCrash"
        private const val PREFS_NAME = "lotterynet_native_diagnostics"
        private const val KEY_REPORT = "pending_native_crash"
        private const val KEY_LATEST_REPORT = "latest_native_crash"
        private const val LATEST_REPORT_FILE = "last_native_crash.json"
    }
}
