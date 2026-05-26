package com.lotterynet.pro.ui.update

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lotterynet.pro.core.update.OtaUpdateInfo
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import org.json.JSONObject

class UpdatePromptActivity : AppCompatActivity() {
    private lateinit var updateInfo: OtaUpdateInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateInfo = runCatching {
            OtaUpdateInfo.fromJson(JSONObject(intent.getStringExtra(EXTRA_UPDATE_JSON).orEmpty()))
        }.getOrNull() ?: run {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(updateInfo.blocksCurrentBuild) {
                override fun handleOnBackPressed() = Unit
            },
        )
        setFinishOnTouchOutside(!updateInfo.blocksCurrentBuild)
        setContent {
            LotteryNetComposeTheme {
                val viewModel: UpdateViewModel = viewModel()
                val state by viewModel.state.collectAsState()
                LaunchedEffect(updateInfo.versionCode) {
                    viewModel.bind(updateInfo)
                }
                UpdateDialog(
                    state = state,
                    fallbackInfo = updateInfo,
                    onUpdateNow = viewModel::startDownload,
                    onRetry = {
                        viewModel.resetToAvailable(it)
                        viewModel.startDownload(it)
                    },
                    onLater = { info ->
                        viewModel.dismiss(info) { finish() }
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isShowing = true
    }

    override fun onStop() {
        isShowing = false
        super.onStop()
    }

    companion object {
        const val EXTRA_UPDATE_JSON = "lotterynet_ota_update_json"

        @Volatile
        var isShowing: Boolean = false
    }
}
