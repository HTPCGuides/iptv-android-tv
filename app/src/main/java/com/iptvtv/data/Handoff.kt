package com.iptvtv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** What another app handed over, once read. */
sealed interface Handoff {
    /** A playlist file, read in full — its URI stops being readable once the hand-off is over. */
    data class File(val name: String, val text: String) : Handoff
    /** A playlist given as a web address, which can be kept and refreshed like any other. */
    data class Link(val name: String, val url: String) : Handoff
    data class Failed(val message: String) : Handoff
}

/** Far above any real channel list (iptv-org's full index is about 2 MB), well below what hurts a TV stick. */
private const val MAX_PLAYLIST_BYTES = 20L * 1024 * 1024

/** The playlist [intent] carries: the data of a VIEW, the stream of a SEND. Null for a plain launch. */
fun handedUri(intent: Intent?): Uri? = when (intent?.action) {
    Intent.ACTION_VIEW -> intent.data
    Intent.ACTION_SEND ->
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
    else -> null
}

/** Reads what [uri] points at, off the main thread. */
suspend fun readHandoff(ctx: Context, uri: Uri): Handoff = withContext(Dispatchers.IO) {
    when (uri.scheme?.lowercase()) {
        "http", "https" -> Handoff.Link(playlistName(uri.lastPathSegment), uri.toString())
        "content", "file" -> runCatching {
            val name = playlistName(displayName(ctx, uri) ?: uri.lastPathSegment)
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > MAX_PLAYLIST_BYTES) {
                        return@runCatching Handoff.Failed("“$name” is too large to be a playlist.")
                    }
                    out.write(chunk, 0, n)
                }
                out.toByteArray()
            } ?: return@runCatching Handoff.Failed("Couldn't open “$name”.")
            Handoff.File(name, String(bytes, Charsets.UTF_8))
        }.getOrElse { e ->
            Handoff.Failed(
                if (e is SecurityException) "The app that sent this playlist didn't allow it to be read."
                else "Couldn't read that playlist file.",
            )
        }
        else -> Handoff.Failed("That isn't a playlist this app can open.")
    }
}

/** The name the sending app or file manager shows for [uri], if it will say. */
private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

/** "sports.m3u8" → "sports". */
fun playlistName(raw: String?): String =
    raw.orEmpty()
        .substringAfterLast('/')
        .replace(Regex("""\.m3u8?$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\p{Cntrl}"""), " ")
        .trim()
        .ifBlank { "Imported playlist" }
