package com.example.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

enum class AccountProviderBadge(val label: String) {
    GMAIL("Gmail / Google"),
    OUTLOOK("Microsoft Outlook"),
    SYSTEM("Device Calendar")
}

data class UnifiedScheduleItem(
    val id: String,
    val title: String,
    val accountName: String,
    val calendarDisplayName: String,
    val providerBadge: AccountProviderBadge,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val location: String,
    val isAllDay: Boolean = false,
    val isCustomLocalItem: Boolean = false,
    val localEntityId: Long? = null,
    val isCompleted: Boolean = false,
    /** Event notes/body from the provider (used to find join links). */
    val description: String = "",
    /** Google Meet / Teams / Zoom / Webex link, if one was found. */
    val joinUrl: String? = null,
    /** Other accounts that hold the same event (same invite sent to several addresses). */
    val alsoInAccounts: List<String> = emptyList()
)

class NativeCalendarReader(private val context: Context) {

    /** Last query error, or null if the last query succeeded. */
    @Volatile
    var lastError: String? = null
        private set

    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Emits whenever any calendar on the device changes (new invite synced, event edited, etc.).
     * Collectors should debounce, since providers often fire several notifications per sync.
     */
    fun observeCalendarChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        try {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot observe calendar changes", e)
        }
        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun queryUpcomingSystemEvents(windowHours: Int = 48): List<UnifiedScheduleItem> =
        withContext(Dispatchers.IO) {
            if (!hasCalendarPermission()) {
                lastError = null
                return@withContext emptyList()
            }

            val results = mutableListOf<UnifiedScheduleItem>()
            val now = System.currentTimeMillis()
            // Look back 24h so all-day events (stored as UTC midnight) are not missed.
            val startRange = now - (24 * 60 * 60 * 1000L)
            val endRange = now + (windowHours * 60 * 60 * 1000L)

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startRange)
            ContentUris.appendId(builder, endRange)

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.STATUS,
                CalendarContract.Instances.SELF_ATTENDEE_STATUS,
                CalendarContract.Instances.VISIBLE,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME
            )

            try {
                context.contentResolver.query(
                    builder.build(),
                    projection,
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                    val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                    val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                    val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                    val descIdx = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                    val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                    val statusIdx = cursor.getColumnIndex(CalendarContract.Instances.STATUS)
                    val selfStatusIdx = cursor.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS)
                    val visibleIdx = cursor.getColumnIndex(CalendarContract.Instances.VISIBLE)
                    val accNameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                    val accTypeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                    val calNameIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        // Skip cancelled events, declined invites, and hidden calendars.
                        if (statusIdx >= 0 && !cursor.isNull(statusIdx) &&
                            cursor.getInt(statusIdx) == CalendarContract.Events.STATUS_CANCELED
                        ) continue
                        if (selfStatusIdx >= 0 && !cursor.isNull(selfStatusIdx) &&
                            cursor.getInt(selfStatusIdx) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                        ) continue
                        if (visibleIdx >= 0 && !cursor.isNull(visibleIdx) && cursor.getInt(visibleIdx) == 0) continue

                        val eventId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                        val title = (if (titleIdx >= 0) cursor.getString(titleIdx) else null)
                            ?.takeIf { it.isNotBlank() } ?: "Scheduled Event"
                        val rawBegin = if (beginIdx >= 0) cursor.getLong(beginIdx) else now
                        val rawEnd = if (endIdx >= 0) cursor.getLong(endIdx) else (rawBegin + 3600_000L)
                        val location = (if (locIdx >= 0) cursor.getString(locIdx) else null).orEmpty()
                        val description = (if (descIdx >= 0) cursor.getString(descIdx) else null).orEmpty()
                        val allDay = (if (allDayIdx >= 0) cursor.getInt(allDayIdx) else 0) == 1
                        val accountName = (if (accNameIdx >= 0) cursor.getString(accNameIdx) else null).orEmpty()
                        val accountType = (if (accTypeIdx >= 0) cursor.getString(accTypeIdx) else null).orEmpty()
                        val calName = (if (calNameIdx >= 0) cursor.getString(calNameIdx) else null).orEmpty()

                        // All-day instances are stored as UTC midnights; shift them to local midnights
                        // so they land on the right day in every time zone.
                        val begin = if (allDay) utcMidnightToLocalMidnight(rawBegin) else rawBegin
                        val end = if (allDay) utcMidnightToLocalMidnight(rawEnd) else rawEnd
                        if (end < now - (2 * 60 * 60 * 1000L)) continue

                        val badge = classifyProvider(accountName, accountType, calName)

                        results.add(
                            UnifiedScheduleItem(
                                id = "sys_${eventId}_$begin",
                                title = title,
                                accountName = accountName.ifBlank { "Device Account" },
                                calendarDisplayName = calName.ifBlank { badge.label },
                                providerBadge = badge,
                                startTimeMillis = begin,
                                endTimeMillis = end,
                                location = location,
                                isAllDay = allDay,
                                isCustomLocalItem = false,
                                description = description,
                                joinUrl = MeetingLinks.find(location, description)
                            )
                        )
                    }
                }
                lastError = null
            } catch (e: SecurityException) {
                Log.w(TAG, "Calendar permission revoked during query", e)
                lastError = "Calendar permission was revoked"
                return@withContext emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Calendar query failed", e)
                lastError = e.message ?: "Calendar query failed"
                return@withContext emptyList()
            }

            deduplicate(results)
        }

    fun getSyncedCalendarAccountsSummary(): List<Pair<String, AccountProviderBadge>> {
        if (!hasCalendarPermission()) return emptyList()
        val accounts = linkedMapOf<String, AccountProviderBadge>()
        val projection = arrayOf(
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val dispIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val accName = (if (nameIdx >= 0) cursor.getString(nameIdx) else null).orEmpty()
                    val accType = (if (typeIdx >= 0) cursor.getString(typeIdx) else null).orEmpty()
                    val disp = (if (dispIdx >= 0) cursor.getString(dispIdx) else null).orEmpty()
                    if (accName.isNotBlank()) {
                        accounts[accName] = classifyProvider(accName, accType, disp)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to list calendar accounts", e)
        }
        return accounts.entries.map { it.key to it.value }
    }

    companion object {
        private const val TAG = "NativeCalendarReader"

        fun classifyProvider(
            accountName: String,
            accountType: String,
            calendarName: String
        ): AccountProviderBadge {
            val combined = "${accountName.lowercase()} ${accountType.lowercase()} ${calendarName.lowercase()}"
            return when {
                combined.contains("outlook") ||
                    combined.contains("microsoft") ||
                    combined.contains("hotmail") ||
                    combined.contains("live.com") ||
                    combined.contains("office365") ||
                    combined.contains("exchange") -> AccountProviderBadge.OUTLOOK
                combined.contains("gmail") ||
                    combined.contains("com.google") ||
                    combined.contains("google") -> AccountProviderBadge.GMAIL
                else -> AccountProviderBadge.SYSTEM
            }
        }

        /**
         * Merges copies of the same meeting that appear in several accounts (e.g. an invite sent
         * to both a Gmail and an Outlook address). The first copy is kept; the others are listed
         * in [UnifiedScheduleItem.alsoInAccounts]. A join link from any copy is preserved.
         */
        fun deduplicate(items: List<UnifiedScheduleItem>): List<UnifiedScheduleItem> {
            return items
                .groupBy { Triple(it.title.trim().lowercase(), it.startTimeMillis, it.endTimeMillis) }
                .values
                .map { copies ->
                    val primary = copies.first()
                    if (copies.size == 1) {
                        primary
                    } else {
                        primary.copy(
                            joinUrl = primary.joinUrl ?: copies.firstNotNullOfOrNull { it.joinUrl },
                            alsoInAccounts = copies.drop(1)
                                .map { it.accountName }
                                .filter { it != primary.accountName }
                                .distinct()
                        )
                    }
                }
                .sortedBy { it.startTimeMillis }
        }

        private fun utcMidnightToLocalMidnight(utcMillis: Long): Long {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
            return Calendar.getInstance().apply {
                clear()
                set(
                    utc.get(Calendar.YEAR),
                    utc.get(Calendar.MONTH),
                    utc.get(Calendar.DAY_OF_MONTH),
                    0,
                    0,
                    0
                )
            }.timeInMillis
        }
    }
}
