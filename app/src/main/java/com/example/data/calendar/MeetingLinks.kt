package com.example.data.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Detects video-call join links in calendar event fields and opens them. */
object MeetingLinks {

    private val patterns = listOf(
        Regex("""https?://meet\.google\.com/[a-z]{3,4}-[a-z]{3,4}-[a-z]{3,4}[^\s"'<>]*""", RegexOption.IGNORE_CASE),
        Regex("""https?://teams\.microsoft\.com/l/meetup-join/[^\s"'<>]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://teams\.live\.com/meet/[^\s"'<>]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:[a-z0-9-]+\.)?zoom\.us/(?:j|my|w|s)/[^\s"'<>]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://[a-z0-9-]+\.webex\.com/[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    )

    /** Returns the first join link found in [fields], checked in order (location first, then description). */
    fun find(vararg fields: String?): String? {
        for (field in fields) {
            if (field.isNullOrBlank()) continue
            for (pattern in patterns) {
                val match = pattern.find(field) ?: continue
                return match.value.trimEnd('>', ')', ']', '.', ',', ';')
            }
        }
        return null
    }

    fun platformLabel(url: String): String {
        val lower = url.lowercase()
        return when {
            "meet.google.com" in lower -> "Google Meet"
            "teams.microsoft.com" in lower || "teams.live.com" in lower -> "Teams"
            "zoom.us" in lower -> "Zoom"
            "webex.com" in lower -> "Webex"
            else -> "Call"
        }
    }

    fun open(context: Context, url: String): Boolean {
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("MeetingLinks", "No app can open $url", e)
            false
        }
    }
}
