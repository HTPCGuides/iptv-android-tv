package com.iptvtv.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How a saved Ace Stream version is named. The title says when it was built, in the
 * user's own date format — "Ace Stream · 28 Oct 2026, 14:05" — so a list of versions reads
 * as a history; the file name is the same moment, human-readable and sortable.
 */
object AceVersionName {
    fun title(atMs: Long, locale: Locale = Locale.getDefault()): String =
        "Ace Stream · " + SimpleDateFormat("d MMM yyyy, HH:mm", locale).format(Date(atMs))

    /** "ace-stream-2026-10-28-1405.m3u8" */
    fun fileName(atMs: Long): String =
        "ace-stream-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date(atMs)) + ".m3u8"

    /** Sorts by build time, newest last; seconds keep two builds in one minute apart. */
    fun id(atMs: Long): String =
        com.iptvtv.model.Source.ACE_ID_PREFIX + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date(atMs))
}
