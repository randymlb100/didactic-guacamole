package com.lotterynet.pro.ui.login

import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.R
import com.lotterynet.pro.core.auth.LocalAuthenticator
import com.lotterynet.pro.core.auth.NativeSessionAuthRepository
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter
import com.lotterynet.pro.core.diagnostics.NativeCrashReport
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.SavedLogin
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.NativeAuthRepository
import com.lotterynet.pro.core.repository.NativeBootstrapRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.sync.BootstrapResult
import com.lotterynet.pro.core.sync.NativeBootstrapRepositoryImpl
import com.lotterynet.pro.core.sync.NativeUsersBootstrapper
import com.lotterynet.pro.ui.common.BrandLogo
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.AdaptiveScreenContract
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.resolveAdaptiveScreenContract
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.navigation.nativeHomeIntent
import com.lotterynet.pro.ui.navigation.resolveNativeHomeRoute
import com.lotterynet.pro.ui.shell.ShellActivity
import com.lotterynet.pro.ui.shell.resolveStartupRuntimePermissions
import kotlin.concurrent.thread

internal data class LoginLayoutContract(
    val usePanel: Boolean,
    val compactPanel: Boolean,
    val showSupportingText: Boolean,
    val sectionSpacingDp: Int,
    val panelPaddingDp: Int,
)

internal data class LoginBootstrapGate(
    val blockEntryWhileBootstrapping: Boolean,
)

internal data class LoginSubmitGate(
    val enabled: Boolean,
    val status: String? = null,
)

internal data class LoginCopyContract(
    val slogan: String,
    val showSlogan: Boolean,
)

internal data class PasswordVisibilityContract(
    val actionLabel: String,
    val maskPassword: Boolean,
)

internal data class LoginAttemptResult(
    val success: Boolean,
    val message: String? = null,
)

internal fun resolveLoginCopyContract(): LoginCopyContract {
    return LoginCopyContract(
        slogan = "Venta rapida, caja clara, tickets seguros.",
        showSlogan = true,
    )
}

internal fun resolvePasswordVisibilityContract(showPassword: Boolean): PasswordVisibilityContract {
    return PasswordVisibilityContract(
        actionLabel = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
        maskPassword = !showPassword,
    )
}

internal fun resolveInitialLoginPassword(savedLogin: SavedLogin?): String {
    return savedLogin
        ?.takeIf { it.remember }
        ?.password
        .orEmpty()
}

internal fun resolveBlockedLoginStatusMessage(
    username: String,
    accounts: List<UserAccount>,
): String? {
    val normalized = username.trim()
    if (normalized.isBlank()) return null
    val account = accounts.firstOrNull { account ->
        account.user.equals(normalized, ignoreCase = true) ||
            account.id.equals(normalized, ignoreCase = true)
    } ?: return null
    if (account.active) return null
    return when (account.role) {
        UserRole.ADMIN -> "Esta cuenta está bloqueada por Master."
        UserRole.SUPERVISOR -> "Tus credenciales están bloqueadas por admin."
        UserRole.CASHIER -> "Tus credenciales están bloqueadas por admin."
        else -> null
    }
}

internal fun resolveLoginBootstrapGate(hasCachedUsers: Boolean): LoginBootstrapGate {
    return LoginBootstrapGate(
        blockEntryWhileBootstrapping = !hasCachedUsers,
    )
}

internal fun resolveLoginSubmitGate(
    bootstrapBusy: Boolean,
    loginBusy: Boolean,
): LoginSubmitGate {
    return LoginSubmitGate(
        enabled = !bootstrapBusy && !loginBusy,
        status = if (loginBusy) "Verificando credenciales..." else null,
    )
}

internal fun resolveLoginLayoutContract(windowMode: LotteryNetWindowMode): LoginLayoutContract {
    val adaptive = resolveAdaptiveScreenContract(windowMode)
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    return LoginLayoutContract(
        usePanel = windowMode == LotteryNetWindowMode.TABLET || windowMode == LotteryNetWindowMode.WIDE,
        compactPanel = compact,
        showSupportingText = adaptive.showSupportingText,
        sectionSpacingDp = when (windowMode) {
            LotteryNetWindowMode.POS_TIGHT -> 8
            LotteryNetWindowMode.POS -> 10
            else -> adaptive.sectionSpacing.value.toInt()
        },
        panelPaddingDp = when (windowMode) {
            LotteryNetWindowMode.POS_TIGHT -> 6
            LotteryNetWindowMode.POS -> 8
            else -> 12
        },
    )
}

class LoginActivity : AppCompatActivity() {
    private lateinit var sessionRepository: LocalSessionRepository
    private lateinit var usersRepository: LocalUsersRepository
    private lateinit var authenticator: LocalAuthenticator
    private lateinit var usersBootstrapper: NativeUsersBootstrapper
    private lateinit var authRepository: NativeAuthRepository
    private lateinit var bootstrapRepository: NativeBootstrapRepository
    private lateinit var crashReporter: NativeCrashReporter
    private var forceMenuAfterCrash: Boolean = false
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        crashReporter = NativeCrashReporter(this)
        sessionRepository = LocalSessionRepository(this)
        usersRepository = LocalUsersRepository(this)
        authenticator = LocalAuthenticator(usersRepository)
        usersBootstrapper = NativeUsersBootstrapper(usersRepository)
        authRepository = NativeSessionAuthRepository(
            authenticator = authenticator,
            sessionRepository = sessionRepository,
            usersRepository = usersRepository,
        )
        bootstrapRepository = NativeBootstrapRepositoryImpl(usersBootstrapper)
        val latestCrash = crashReporter.peekLatest()
        val pendingCrash = crashReporter.consumePending()
        if (pendingCrash != null) {
            forceMenuAfterCrash = pendingCrash.activityName == "SalesActivity" ||
                pendingCrash.source?.contains("SalesActivity", ignoreCase = true) == true
            sessionRepository.saveActiveSession(null)
            sessionRepository.saveSessionSnapshot(null)
        }
        sessionRepository.getActiveSession()?.let { session ->
            startActivity(homeIntentFor(session.role))
            finish()
            return
        }
        val saved = sessionRepository.getSavedLogin()
        val bootstrapGate = resolveLoginBootstrapGate(usersRepository.hasCachedUsers())
        setContent {
            com.lotterynet.pro.ui.theme.LotteryNetComposeTheme {
                LoginRoute(
                    initialUser = saved?.username.orEmpty(),
                    initialPassword = resolveInitialLoginPassword(saved),
                    initialRemember = saved?.remember ?: true,
                    blockEntryWhileBootstrapping = bootstrapGate.blockEntryWhileBootstrapping,
                    initialStatus = intent?.getStringExtra(EXTRA_LOGIN_STATUS)
                        ?: (pendingCrash ?: latestCrash?.takeIf(::shouldShowCrashStatusOnLogin))?.toUserMessage(),
                    initialStatusIsError = intent?.hasExtra(EXTRA_LOGIN_STATUS) == true || pendingCrash != null || shouldShowCrashStatusOnLogin(latestCrash),
                    onBootstrap = { onDone ->
                        thread {
                            val result = bootstrapRepository.bootstrapUsers()
                            runOnUiThread { onDone(result) }
                        }
                    },
                    onEnter = { username, password, remember, onDone ->
                        thread(name = "login-authenticate") {
                            val result = authenticateAndEnter(username, password, remember)
                            runOnUiThread { onDone(result) }
                        }
                    },
                )
            }
        }
        Handler(Looper.getMainLooper()).post {
            requestStartupRuntimePermissions()
        }
    }

    private fun authenticateAndEnter(username: String, password: String, remember: Boolean): LoginAttemptResult {
        val localBlockedMessage = resolveBlockedLoginStatusMessage(
            username,
            usersRepository.getAdmins() + usersRepository.getSupervisors() + usersRepository.getCashiers(),
        )
        if (localBlockedMessage != null) {
            runCatching { usersBootstrapper.bootstrap(forceRemoteRefresh = true) }
        }
        resolveBlockedLoginStatusMessage(username, usersRepository.getAdmins() + usersRepository.getSupervisors() + usersRepository.getCashiers())?.let { message ->
            return LoginAttemptResult(success = false, message = message)
        }
        val session = authRepository.authenticate(username, password, remember)
            ?: return LoginAttemptResult(success = false)
        startActivity(homeIntentFor(session.role, forceMenuAfterCrash).apply {
            putExtra("native_login_user", username)
            putExtra("native_login_password", password)
        })
        finish()
        return LoginAttemptResult(success = true)
    }

    private fun homeIntentFor(role: UserRole, forceMenu: Boolean = false): Intent {
        if (!forceMenu) {
            return nativeHomeIntent(this, resolveNativeHomeRoute(role)).apply {
                putExtra("native_role_hint", role.name)
            }
        }
        return Intent(this, ShellActivity::class.java).apply {
            putExtra(ShellActivity.EXTRA_FORCE_MENU, true)
            putExtra("native_force_menu_after_crash", forceMenu)
            putExtra("native_role_hint", role.name)
        }
    }

    private fun requestStartupRuntimePermissions() {
        val missing = resolveStartupRuntimePermissions(Build.VERSION.SDK_INT).filter { permission ->
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    companion object {
        const val EXTRA_LOGIN_STATUS = "lotterynet_login_status"
    }
}

internal fun shouldShowCrashStatusOnLogin(report: NativeCrashReport?): Boolean {
    return report?.isUnhandledCrash() == true
}

@Composable
private fun LoginRoute(
    initialUser: String,
    initialPassword: String,
    initialRemember: Boolean,
    blockEntryWhileBootstrapping: Boolean,
    initialStatus: String?,
    initialStatusIsError: Boolean,
    onBootstrap: (((BootstrapResult) -> Unit) -> Unit),
    onEnter: (String, String, Boolean, (LoginAttemptResult) -> Unit) -> Unit,
) {
    var username by remember(initialUser) { mutableStateOf(initialUser) }
    var password by remember(initialPassword) { mutableStateOf(initialPassword) }
    var rememberUser by remember(initialRemember) { mutableStateOf(initialRemember) }
    var showPassword by remember { mutableStateOf(false) }
    var status by remember(initialStatus) { mutableStateOf(initialStatus) }
    var statusIsError by remember(initialStatusIsError) { mutableStateOf(initialStatusIsError) }
    var bootstrapBusy by remember(blockEntryWhileBootstrapping) { mutableStateOf(blockEntryWhileBootstrapping) }
    var loginBusy by remember { mutableStateOf(false) }
    val visual = rememberLotteryNetVisualSpec()
    val invalidLoginText = stringResource(R.string.native_login_error_invalid)
    val bootstrapReadyText = stringResource(R.string.native_login_bootstrap_ready)
    val bootstrapErrorText = stringResource(R.string.native_login_bootstrap_error)
    val bootstrapLoadingText = stringResource(R.string.native_login_bootstrap_loading)
    val requiredText = stringResource(R.string.native_login_error_required)
    val loginTitle = stringResource(R.string.native_login_title)
    val loginSubtitle = stringResource(R.string.native_login_subtitle)
    val loginSupporting = stringResource(R.string.native_login_supporting)
    val adaptive = resolveAdaptiveScreenContract(visual.windowMode)
    val loginLayout = resolveLoginLayoutContract(visual.windowMode)
    val loginCopy = resolveLoginCopyContract()
    val passwordVisibility = resolvePasswordVisibilityContract(showPassword)

    LaunchedEffect(Unit) {
        onBootstrap { result ->
            bootstrapBusy = false
            val nextStatus = if (result.ok) {
                bootstrapReadyText
            } else {
                result.message ?: bootstrapErrorText
            }
            val nextIsError = !result.ok
            if (status.isNullOrBlank() || !statusIsError) {
                status = nextStatus
                statusIsError = nextIsError
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .background(visual.colors.background)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            val compact = maxWidth < 720.dp
            val tight = visual.windowMode == LotteryNetWindowMode.POS_TIGHT
            val panelWidth = if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(max = 396.dp)
            val inlineStatus = when {
                !status.isNullOrBlank() -> status
                loginBusy -> resolveLoginSubmitGate(bootstrapBusy, loginBusy).status
                bootstrapBusy -> bootstrapLoadingText
                else -> null
            }

            val formContent: @Composable ColumnScope.() -> Unit = {
                ComposeInput(
                    value = username,
                    label = stringResource(R.string.native_login_user),
                    leading = { Icon(Icons.Rounded.Badge, null) },
                    keyboardType = KeyboardType.Text,
                    onValueChange = {
                        username = it
                        status = null
                    },
                )

                Spacer(modifier = Modifier.height(adaptive.contentSpacing))

                ComposeInput(
                    value = password,
                    label = stringResource(R.string.native_login_password),
                    leading = { Icon(Icons.Rounded.Lock, null) },
                    trailing = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = passwordVisibility.actionLabel,
                            )
                        }
                    },
                    keyboardType = KeyboardType.Password,
                    visualPassword = passwordVisibility.maskPassword,
                    onValueChange = {
                        password = it
                        status = null
                    },
                )

                RowToggle(
                    checked = rememberUser,
                    label = stringResource(R.string.native_login_remember),
                    onCheckedChange = { rememberUser = it },
                )

                inlineStatus?.let { message ->
                    LoginInlineStatus(
                        message = message,
                        tone = when {
                            bootstrapBusy && status.isNullOrBlank() -> Color(0xFF475569)
                            statusIsError -> Color(0xFF9F1239)
                            else -> Color(0xFF166534)
                        },
                    )
                }

                CompactActionButton(
                    label = stringResource(R.string.native_login_enter),
                    onClick = {
                        when {
                            bootstrapBusy || loginBusy -> {
                                status = bootstrapLoadingText
                                statusIsError = false
                            }

                            username.isBlank() || password.isBlank() -> {
                                status = requiredText
                                statusIsError = true
                            }

                            else -> {
                                loginBusy = true
                                status = resolveLoginSubmitGate(bootstrapBusy, loginBusy).status
                                statusIsError = false
                                onEnter(username.trim(), password.trim(), rememberUser) { result ->
                                    loginBusy = false
                                    if (!result.success) {
                                        status = result.message ?: invalidLoginText
                                        statusIsError = true
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = resolveLoginSubmitGate(bootstrapBusy, loginBusy).enabled,
                    active = true,
                    icon = Icons.AutoMirrored.Rounded.Login,
                )

            }
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (tight) Arrangement.Top else Arrangement.Center,
            ) {
                Column(
                    modifier = panelWidth,
                    horizontalAlignment = Alignment.Start,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BrandLogo(
                            modifier = Modifier
                                .size(if (tight) 28.dp else 30.dp)
                                .background(visual.colors.chrome, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            tintColor = Color.White,
                        )
                        Text(
                            text = "LotteryNet Pro",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = visual.colors.ink,
                        )
                    }
                    if (loginCopy.showSlogan) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = loginCopy.slogan,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = visual.colors.tickets,
                        )
                    }
                    Spacer(modifier = Modifier.height(if (tight) 4.dp else 6.dp))
                    Text(
                        text = loginTitle,
                        style = if (tight) {
                            MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        },
                        color = visual.colors.ink,
                    )
                    if (loginLayout.showSupportingText && !compact) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = loginSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = visual.colors.muted,
                            textAlign = TextAlign.Start,
                        )
                    }
                    Spacer(modifier = Modifier.height(adaptive.sectionSpacing))

                    if (loginLayout.usePanel) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = visual.colors.panel,
                            border = BorderStroke(1.dp, visual.colors.border),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(loginLayout.panelPaddingDp.dp),
                                verticalArrangement = Arrangement.spacedBy(adaptive.contentSpacing),
                                content = formContent,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(adaptive.contentSpacing),
                            content = formContent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginInlineStatus(
    message: String,
    tone: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = tone.copy(alpha = 0.08f),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.18f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tone,
        )
    }
}

@Composable
private fun ComposeInput(
    value: String,
    label: String,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType,
    visualPassword: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = leading,
        trailingIcon = trailing,
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Bold,
            color = visual.colors.ink,
        ),
        visualTransformation = if (visualPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = visual.colors.panel,
            unfocusedContainerColor = visual.colors.panel,
            focusedIndicatorColor = visual.colors.ink,
            unfocusedIndicatorColor = visual.colors.border,
            focusedLeadingIconColor = visual.colors.ink,
            unfocusedLeadingIconColor = visual.colors.neutral,
            focusedLabelColor = visual.colors.ink,
            unfocusedLabelColor = visual.colors.muted,
            focusedTextColor = visual.colors.ink,
            unfocusedTextColor = visual.colors.ink,
            cursorColor = visual.colors.ink,
        ),
    )
}

@Composable
private fun RowToggle(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = visual.colors.ink,
                uncheckedColor = visual.colors.border,
                checkmarkColor = Color.White,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = visual.colors.muted,
        )
    }
}
