package com.wwwescape.streamanything.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Opens [url] in the user's browser. Only web links are allowed — some URLs shown here come from
 * outside the app (e.g. a link pasted by the user), and must never be able to launch an
 * `intent:`/`file:`/`content:` target. A device with no browser (common on TVs) is a no-op rather
 * than a crash. */
fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme?.lowercase() !in setOf("https", "http")) {
        Log.w("UrlLauncher", "Refusing to open non-web URL")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("UrlLauncher", "No app available to open URL", e)
    }
}
