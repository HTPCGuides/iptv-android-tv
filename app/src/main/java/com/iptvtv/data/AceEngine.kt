package com.iptvtv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The Ace Stream engine on this device: whether its app is installed, whether the engine
 * is answering, and the two things that put it right when it isn't — opening the app,
 * which starts the engine, and the app's own settings page, where "Battery → Unrestricted"
 * stops the phone killing it in the background (the actual cause of "keeps stopping").
 */
class AceEngine(private val ctx: Context) {
    /** Whichever Ace Stream package is installed, if any. Needs the <queries> in the manifest. */
    val installedPackage: String? by lazy {
        PACKAGES.firstOrNull { runCatching { ctx.packageManager.getPackageInfo(it, 0) }.isSuccess }
    }
    val installed get() = installedPackage != null

    /** Asks the engine's local API for its version; false when nothing answers within a moment. */
    suspend fun running(addr: String = "127.0.0.1:6878"): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("http://$addr$STATUS_PATH").openConnection() as HttpURLConnection).apply {
                connectTimeout = 1_500; readTimeout = 1_500
            }
            try { conn.responseCode in 200..299 } finally { conn.disconnect() }
        }.getOrDefault(false)
    }

    /** Opens the Ace Stream app, which starts its engine. */
    fun open(): Intent? =
        installedPackage?.let { ctx.packageManager.getLaunchIntentForPackage(it) }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The app's page in Settings — Battery → Unrestricted is two taps from here. */
    fun appSettings(): Intent? = installedPackage?.let {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$it")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        private val PACKAGES = listOf("org.acestream.media", "org.acestream.node", "org.acestream.core")
        private const val STATUS_PATH = "/webui/api/service?method=get_version"
    }
}
