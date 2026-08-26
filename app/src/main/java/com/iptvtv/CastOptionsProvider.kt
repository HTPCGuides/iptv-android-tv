package com.iptvtv

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Cast configuration. The framework finds this class by name from a manifest
 * meta-data entry, so it must stay public and keep its fully-qualified name.
 *
 * The receiver is Google's **Default Media Receiver** rather than a custom one.
 * A registered receiver would mean a Cast developer account, a hosted HTML
 * receiver page and an app ID per build — a lot of apparatus for what this app
 * needs, which is "put this URL on that TV". The default receiver plays HLS,
 * DASH and MP4, which covers most of an IPTV list.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            // The system media notification and lock-screen controls come free
            // with the framework; leaving them on means a cast started here
            // behaves like one started from YouTube or Plex.
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
