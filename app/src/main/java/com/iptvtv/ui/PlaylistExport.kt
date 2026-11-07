package com.iptvtv.ui

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** A playlist's file handed to other apps: Share through the system sheet, or Save to Downloads. */
object PlaylistExport {
    /** False when nothing on the device can receive it. */
    fun share(ctx: Context, fileName: String, text: String): Boolean {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // one at a time; yesterday's has been handed over
        val file = File(dir, fileName).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("audio/x-mpegurl")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, fileName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            ctx.startActivity(Intent.createChooser(send, "Share playlist").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Download needs no permission from Android 10; before that the option isn't offered. */
    val canSave get() = Build.VERSION.SDK_INT >= 29

    /** Writes to Download/IPTV TV; returns the folder and name it landed under, or null. */
    fun saveToDownloads(ctx: Context, fileName: String, text: String): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val folder = Environment.DIRECTORY_DOWNLOADS + "/IPTV TV"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-mpegurl")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
            "$folder/$fileName"
        }.getOrElse { resolver.delete(uri, null, null); null }
    }
}
