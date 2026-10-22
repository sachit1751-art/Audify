/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.sachit.innertube.YouTube
import com.sachit.innertube.models.YouTubeAccount
import com.sachit.innertube.utils.parseCookieString
import com.sachit.music.LocalPlayerAwareWindowInsets
import com.sachit.music.R
import com.sachit.music.constants.AccountChannelHandleKey
import com.sachit.music.constants.AccountEmailKey
import com.sachit.music.constants.AccountNameKey
import com.sachit.music.constants.DataSyncIdKey
import com.sachit.music.constants.InnerTubeAuthUserKey
import com.sachit.music.constants.InnerTubeCookieKey
import com.sachit.music.constants.VisitorDataKey
import com.sachit.music.ui.utils.backToMain
import com.sachit.music.utils.SecureCookieStore
import com.sachit.music.utils.reportException
import com.sachit.music.utils.safeDataStoreEdit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import timber.log.Timber
import kotlin.coroutines.resume

private enum class LoginStage {
    Authenticating,
    CheckingAccounts,
    SelectingAccount,
    SwitchingAccount,
    Completing,
}

private data class AuthData(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val authUser: String,
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    isSwitchingChannel: Boolean = false,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var loginStage by remember { mutableStateOf(LoginStage.Authenticating) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var accounts by remember { mutableStateOf<List<YouTubeAccount>>(emptyList()) }
    var selectedAccount by remember { mutableStateOf<YouTubeAccount?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun extractAuthData(webView: WebView?): AuthData? {
        val view = webView ?: return null
        var hasAuthCookie = false
        var hasVisitorData = false
        repeat(20) {
            val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
            val cookieMap = runCatching { parseCookieString(cookie) }.getOrDefault(emptyMap())
            // The session values are read straight out of the page with
            // evaluateJavascript: no JavaScript bridge is exposed to the WebView,
            // so a page that manages to run script cannot call back into the app.
            val visitorData = view.evaluateJsExpression(EXTRACT_VISITOR_DATA_JS)
            val dataSyncId = view.evaluateJsExpression(EXTRACT_DATA_SYNC_ID_JS)
            val authUser = view.evaluateJsExpression(EXTRACT_AUTH_USER_JS)

            hasAuthCookie = "SAPISID" in cookieMap
            hasVisitorData = !visitorData.isNullOrBlank()
            if (hasAuthCookie && !visitorData.isNullOrBlank()) {
                return AuthData(
                    cookie = cookie,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId.orEmpty().substringBefore("||"),
                    authUser = authUser.orEmpty().filter(Char::isDigit).ifBlank { "0" },
                )
            }
            delay(500)
        }

        Timber.w(
            "Login: Timed out waiting for the YouTube Music session " +
                "(authCookie=$hasAuthCookie, visitorData=$hasVisitorData)",
        )
        return null
    }

    suspend fun finalizeLogin(authData: AuthData) {
        loginStage = LoginStage.Completing
        YouTube.cookie = authData.cookie
        YouTube.visitorData = authData.visitorData
        YouTube.dataSyncId = authData.dataSyncId
        YouTube.authUser = authData.authUser

        try {
            val accountInfo = YouTube.accountInfo().getOrThrow()
            val saved =
                withContext(Dispatchers.IO) {
                    context.safeDataStoreEdit { settings ->
                        // Encrypted at rest: this cookie grants full account access.
                        settings[InnerTubeCookieKey] = SecureCookieStore.encode(authData.cookie)
                        settings[VisitorDataKey] = authData.visitorData
                        settings[DataSyncIdKey] = authData.dataSyncId
                        settings[InnerTubeAuthUserKey] = authData.authUser
                        settings[AccountNameKey] = accountInfo.name
                        settings[AccountEmailKey] = accountInfo.email.orEmpty()
                        settings[AccountChannelHandleKey] = accountInfo.channelHandle.orEmpty()
                    }
                }
            check(saved) { "Failed to persist account data" }

            webViewRef?.apply {
                stopLoading()
                clearHistory()
                clearCache(true)
                clearFormData()
            }
            CookieManager.getInstance().flush()

            withContext(Dispatchers.Main) {
                context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
                    ?.let(context::startActivity)
                Runtime.getRuntime().exit(0)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Exception) {
            Timber.e(throwable, "Login: Failed to validate or save the selected YouTube channel")
            reportException(throwable)
            errorMessage =
                context.getString(
                    R.string.login_failed_with_error,
                    throwable.message ?: context.getString(R.string.error_unknown),
                )
            loginStage = LoginStage.Authenticating
        }
    }

    suspend fun checkAvailableAccounts(authData: AuthData) {
        loginStage = LoginStage.CheckingAccounts
        YouTube.cookie = authData.cookie
        YouTube.visitorData = authData.visitorData
        YouTube.dataSyncId = authData.dataSyncId
        YouTube.authUser = authData.authUser

        val availableAccounts =
            YouTube.accountsList().getOrElse { throwable ->
                Timber.e(throwable, "Login: Failed to retrieve YouTube channels")
                if (isSwitchingChannel) {
                    errorMessage = context.getString(R.string.youtube_channels_failed)
                    loginStage = LoginStage.Authenticating
                    return
                }
                emptyList()
            }

        if (isSwitchingChannel && availableAccounts.isEmpty()) {
            errorMessage = context.getString(R.string.youtube_channels_failed)
            loginStage = LoginStage.Authenticating
        } else if (availableAccounts.size > 1) {
            val currentAccount =
                availableAccounts.firstOrNull { account ->
                    account.pageId == authData.dataSyncId ||
                        account.dataSyncId?.substringBefore("||") == authData.dataSyncId
                } ?: availableAccounts.firstOrNull { it.isSelected }
            accounts = availableAccounts.map { it.copy(isSelected = it == currentAccount) }
            selectedAccount = accounts.firstOrNull { it.isSelected } ?: accounts.first()
            loginStage = LoginStage.SelectingAccount
        } else {
            finalizeLogin(authData)
        }
    }

    fun handleAuthenticatedPage() {
        when (loginStage) {
            LoginStage.Authenticating,
            LoginStage.SwitchingAccount,
            -> {
                val stage = loginStage
                loginStage = LoginStage.CheckingAccounts
                coroutineScope.launch {
                    val authData =
                        extractAuthData(webViewRef) ?: run {
                            errorMessage = context.getString(R.string.authentication_data_extraction_failed)
                            loginStage = stage
                            return@launch
                        }
                    if (stage == LoginStage.SwitchingAccount) {
                        finalizeLogin(authData)
                    } else {
                        checkAvailableAccounts(authData)
                    }
                }
            }

            else -> Unit
        }
    }

    Box(
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { webViewContext ->
                WebView(webViewContext).apply {
                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val url = request.url
                                val scheme = url.scheme?.lowercase()
                                if (scheme == "http" || scheme == "https") {
                                    // Keep the sign-in flow inside the WebView only for
                                    // Google's own hosts; anything else is handed to the
                                    // browser so the WebView never renders a foreign page
                                    // (and never shares the session cookies with it).
                                    if (isAllowedLoginHost(url.host)) return false
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, url))
                                    }
                                    return true
                                }

                                if (scheme == "intent") {
                                    // Only follow intent:// links that point back at an
                                    // allowed host, so a page cannot launch arbitrary
                                    // apps on the user's behalf.
                                    runCatching {
                                        Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME)
                                            .takeIf { isAllowedLoginHost(it.data?.host) }
                                            ?.apply {
                                                addCategory(Intent.CATEGORY_BROWSABLE)
                                                component = null
                                                selector = null
                                            }?.let(context::startActivity)
                                    }
                                }
                                return true
                            }

                            override fun onPageFinished(
                                view: WebView,
                                url: String?,
                            ) {
                                val pageUri = url?.let(Uri::parse)
                                if (pageUri?.scheme == "https" && pageUri.host == "music.youtube.com") {
                                    handleAuthenticatedPage()
                                }
                            }
                        }
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    webViewRef = this
                    if (isSwitchingChannel) {
                        restoreYouTubeCookies(YouTube.cookie)
                    }
                    loadUrl(
                        if (isSwitchingChannel) {
                            "https://music.youtube.com"
                        } else {
                            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
                        },
                    )
                }
            },
        )

        if (loginStage == LoginStage.CheckingAccounts || loginStage == LoginStage.Completing) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (isSwitchingChannel) R.string.switch_youtube_channel else R.string.login,
                    ),
                )
            },
            navigationIcon = {
                androidx.compose.material3.IconButton(
                    onClick = navController::navigateUp,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
        )
    }

    if (loginStage == LoginStage.SelectingAccount) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.choose_youtube_channel)) },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                ) {
                    Text(
                        text = stringResource(R.string.choose_youtube_channel_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        accounts.forEach { account ->
                            YouTubeAccountPickerItem(
                                account = account,
                                isSelected = account == selectedAccount,
                                onClick = { selectedAccount = account },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedAccount?.let { it.isSelected || it.signinUrl != null } == true,
                    onClick = {
                        val account = selectedAccount ?: return@Button
                        if (account.isSelected) {
                            loginStage = LoginStage.Completing
                            coroutineScope.launch {
                                val authData =
                                    extractAuthData(webViewRef) ?: run {
                                        errorMessage = context.getString(R.string.authentication_data_extraction_failed)
                                        loginStage = LoginStage.Authenticating
                                        return@launch
                                    }
                                finalizeLogin(authData)
                            }
                        } else {
                            val signinUrl = account.signinUrl ?: return@Button
                            val absoluteUrl =
                                when {
                                    signinUrl.startsWith("http://") || signinUrl.startsWith("https://") -> signinUrl
                                    signinUrl.startsWith("/") -> "https://music.youtube.com$signinUrl"
                                    else -> "https://music.youtube.com/$signinUrl"
                                }
                            loginStage = LoginStage.SwitchingAccount
                            webViewRef?.loadUrl(absoluteUrl)
                        }
                    },
                ) {
                    Text(stringResource(R.string.continue_action))
                }
            },
        )
    }

    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(R.string.login_failed)) },
            text = { Text(error) },
            confirmButton = {
                Button(
                    onClick = {
                        errorMessage = null
                        webViewRef?.reload()
                    },
                ) {
                    Text(stringResource(R.string.retry))
                }
            },
        )
    }

    BackHandler(
        enabled = webViewRef?.canGoBack() == true && loginStage == LoginStage.Authenticating && errorMessage == null,
    ) {
        webViewRef?.goBack()
    }
}

@Composable
private fun YouTubeAccountPickerItem(
    account: YouTubeAccount,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (account.thumbnailUrl != null) {
                AsyncImage(
                    model = account.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.account),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                account.channelHandle?.let { channelHandle ->
                    Text(
                        text = channelHandle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                account.byline?.let { byline ->
                    Text(
                        text = byline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            RadioButton(selected = isSelected, onClick = null)
        }
    }
}

private const val EXTRACT_VISITOR_DATA_JS =
    "window.yt&&window.yt.config_?window.yt.config_.VISITOR_DATA:null"

private const val EXTRACT_DATA_SYNC_ID_JS =
    "window.yt&&window.yt.config_?window.yt.config_.DATASYNC_ID:null"

private const val EXTRACT_AUTH_USER_JS =
    "window.yt&&window.yt.config_?String(window.yt.config_.SESSION_INDEX||0):'0'"

private const val JS_EVALUATION_TIMEOUT_MILLIS = 2_000L

/**
 * Evaluates [expression] in the page and returns its value as a plain string.
 *
 * `evaluateJavascript` hands the result back JSON encoded, so a JavaScript
 * string arrives wrapped in quotes and `null` arrives as the literal `null`.
 */
private suspend fun WebView.evaluateJsExpression(expression: String): String? =
    withTimeoutOrNull(JS_EVALUATION_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            evaluateJavascript(expression) { raw ->
                if (continuation.isActive) {
                    continuation.resume(decodeJsValue(raw))
                }
            }
        }
    }

private fun decodeJsValue(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null") return null
    return runCatching {
        when (val value = JSONTokener(raw).nextValue()) {
            is String -> value
            JSONObject.NULL -> null
            else -> value.toString()
        }
    }.getOrNull()
}

/**
 * Hosts the sign-in WebView is allowed to render.
 *
 * The WebView shares the app's cookie jar, so it must never display a page
 * from an untrusted host: everything outside this list is opened in the
 * browser instead.
 */
private val ALLOWED_LOGIN_HOSTS =
    setOf(
        "google.com",
        "youtube.com",
        "youtu.be",
    )

private val ALLOWED_LOGIN_HOST_SUFFIXES =
    listOf(
        ".google.com",
        ".googleapis.com",
        ".gstatic.com",
        ".googleusercontent.com",
        ".youtube.com",
        ".youtu.be",
    )

/** Country specific Google accounts hosts, e.g. `accounts.google.co.uk`. */
private val GOOGLE_COUNTRY_HOST = Regex("^[a-z0-9-]+\\.google\\.[a-z]{2,3}(\\.[a-z]{2})?$")

internal fun isAllowedLoginHost(host: String?): Boolean {
    val normalized = host?.lowercase()?.trimEnd('.') ?: return false
    if (normalized.isEmpty()) return false
    if (normalized in ALLOWED_LOGIN_HOSTS) return true
    if (ALLOWED_LOGIN_HOST_SUFFIXES.any { normalized.endsWith(it) }) return true
    return GOOGLE_COUNTRY_HOST.matches(normalized)
}

private fun restoreYouTubeCookies(cookie: String?) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookie
        ?.let(::parseCookieString)
        .orEmpty()
        .forEach { (name, value) ->
            cookieManager.setCookie(
                "https://music.youtube.com",
                "$name=$value; Domain=.youtube.com; Path=/; Secure",
            )
        }
    cookieManager.flush()
}
