package com.example.data

import com.example.data.agent.AgentResponseResult
import com.example.data.agent.LocalLlmBridge
import com.example.data.calendar.AccountProviderBadge
import com.example.data.calendar.NativeCalendarReader
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.hardware.BatteryTelemetry
import com.example.data.local.AgentMessageEntity
import com.example.data.local.ConnectedAccountEntity
import com.example.data.local.DockAgendaEntity
import com.example.data.local.DockAlarmEntity
import com.example.data.local.DockDao
import com.example.data.local.DockNoteEntity
import com.example.data.weather.CityPreset
import com.example.data.weather.DualCityWeather
import com.example.data.weather.WeatherRepository
import com.example.data.weather.WeatherStationData
import kotlinx.coroutines.flow.Flow

class DockRepository(
    private val dockDao: DockDao,
    private val calendarReader: NativeCalendarReader,
    private val weatherRepository: WeatherRepository,
    private val localLlmBridge: LocalLlmBridge
) {
    val notesFlow: Flow<List<DockNoteEntity>> = dockDao.observeNotes()
    val customAgendaFlow: Flow<List<DockAgendaEntity>> = dockDao.observeCustomAgendaItems()
    val agentMessagesFlow: Flow<List<AgentMessageEntity>> = dockDao.observeAgentMessages()
    val connectedAccountsFlow: Flow<List<ConnectedAccountEntity>> = dockDao.observeConnectedAccounts()
    val alarmsFlow: Flow<List<DockAlarmEntity>> = dockDao.observeAlarms()

    fun hasCalendarPermission(): Boolean = calendarReader.hasCalendarPermission()

    suspend fun fetchNativeCalendarEvents(): List<UnifiedScheduleItem> {
        return calendarReader.queryUpcomingSystemEvents(windowHours = 48)
    }

    /** Emits when the device calendar database changes. */
    fun observeCalendarChanges(): Flow<Unit> = calendarReader.observeCalendarChanges()

    /** Error from the most recent calendar query, or null. */
    val lastCalendarError: String? get() = calendarReader.lastError

    fun getSyncedAccounts(): List<Pair<String, AccountProviderBadge>> {
        return calendarReader.getSyncedCalendarAccountsSummary()
    }

    /**
     * Previously inserted demo Gmail/Outlook accounts and two ENABLED alarms (07:30, 09:00).
     * With real system alarms that would wake new users unexpectedly, so nothing is seeded now.
     */
    suspend fun ensureDefaultAccountsAndAlarmsSeeded() {
        // Intentionally empty: first launch starts clean; onboarding guides the user.
    }

    suspend fun addConnectedAccount(
        emailAddress: String,
        displayName: String,
        providerType: String,
        seedSampleSchedule: Boolean = true
    ): Long {
        val cleanEmail = emailAddress.trim()
        val cleanName = displayName.trim().ifBlank {
            if (providerType.equals("OUTLOOK", ignoreCase = true)) "Outlook Account" else "Gmail Account"
        }
        val normProvider = if (providerType.equals("OUTLOOK", ignoreCase = true)) "OUTLOOK" else "GMAIL"
        val id = dockDao.insertConnectedAccount(
            ConnectedAccountEntity(
                emailAddress = cleanEmail,
                displayName = cleanName,
                providerType = normProvider,
                isSyncEnabled = true
            )
        )
        if (seedSampleSchedule) {
            val start = System.currentTimeMillis() + (15 * 60_000L)
            val end = start + (45 * 60_000L)
            dockDao.insertAgendaItem(
                DockAgendaEntity(
                    title = "$cleanName Sync Briefing",
                    subtitle = cleanEmail,
                    providerType = normProvider,
                    startTimeMillis = start,
                    endTimeMillis = end,
                    locationOrSender = cleanName
                )
            )
        }
        return id
    }

    suspend fun toggleConnectedAccountSync(account: ConnectedAccountEntity) {
        dockDao.setConnectedAccountSyncEnabled(account.id, !account.isSyncEnabled)
    }

    suspend fun removeConnectedAccount(account: ConnectedAccountEntity) {
        dockDao.deleteConnectedAccountById(account.id)
        dockDao.deleteAgendaItemsByAccount(account.emailAddress)
    }

    suspend fun addAlarm(hour: Int, minute: Int, label: String): Long {
        return dockDao.insertAlarm(
            DockAlarmEntity(
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                label = label.trim().ifBlank { "Smart Dock Alarm" },
                isEnabled = true
            )
        )
    }

    suspend fun toggleAlarmEnabled(alarm: DockAlarmEntity) {
        dockDao.setAlarmEnabled(alarm.id, !alarm.isEnabled)
    }

    suspend fun updateAlarmSnooze(id: Long, snoozedUntil: Long?, minuteKey: Long) {
        dockDao.updateAlarmSnooze(id, snoozedUntil, minuteKey)
    }

    suspend fun updateAlarmOptions(
        id: Long,
        repeatDaysMask: Int,
        skipUntilMillis: Long?,
        vibrateOnly: Boolean,
        dismissChallenge: Boolean,
        soundUri: String?
    ) {
        dockDao.updateAlarmOptions(id, repeatDaysMask, skipUntilMillis, vibrateOnly, dismissChallenge, soundUri)
    }

    suspend fun deleteAlarm(id: Long) {
        dockDao.deleteAlarmById(id)
    }

    suspend fun fetchPoitiersAndPittsburghWeather(): DualCityWeather {
        return weatherRepository.fetchDualPoitiersAndPittsburgh()
    }

    suspend fun fetchDualCitiesWeather(
        firstCityName: String,
        secondCityName: String
    ): DualCityWeather {
        return weatherRepository.fetchDualCitiesWeather(firstCityName, secondCityName)
    }

    suspend fun searchWorldwideCities(query: String): List<CityPreset> {
        return weatherRepository.searchWorldwideCities(query)
    }

    suspend fun fetchWeather(cityName: String, lat: Double, lon: Double): Result<WeatherStationData> {
        val preset = WeatherRepository.CITY_PRESETS.find { it.name == cityName }
            ?: CityPreset(cityName, "", "UTC", lat, lon)
        return weatherRepository.fetchLiveWeather(preset)
    }

    suspend fun saveNote(title: String, content: String, category: String, isPinned: Boolean = false) {
        dockDao.insertNote(
            DockNoteEntity(
                title = title.trim(),
                content = content.trim(),
                category = category,
                isPinned = isPinned
            )
        )
    }

    suspend fun toggleNotePinned(note: DockNoteEntity) {
        dockDao.setNotePinned(note.id, !note.isPinned)
    }

    suspend fun deleteNote(id: Long) {
        dockDao.deleteNoteById(id)
    }

    suspend fun addCustomAgendaItem(
        title: String,
        subtitle: String,
        providerType: String,
        startOffsetMinutes: Int,
        durationMinutes: Int,
        locationOrSender: String
    ) {
        val start = System.currentTimeMillis() + (startOffsetMinutes * 60_000L)
        val end = start + (durationMinutes * 60_000L)
        dockDao.insertAgendaItem(
            DockAgendaEntity(
                title = title.trim(),
                subtitle = subtitle.trim(),
                providerType = providerType,
                startTimeMillis = start,
                endTimeMillis = end,
                locationOrSender = locationOrSender.trim()
            )
        )
    }

    suspend fun addCustomAgendaItemWithWindow(
        title: String,
        subtitle: String,
        providerType: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        locationOrSender: String
    ) {
        dockDao.insertAgendaItem(
            DockAgendaEntity(
                title = title.trim(),
                subtitle = subtitle.trim(),
                providerType = providerType,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis.coerceAtLeast(startTimeMillis + 60_000L),
                locationOrSender = locationOrSender.trim()
            )
        )
    }

    suspend fun toggleCustomAgendaCompleted(id: Long, currentCompleted: Boolean) {
        dockDao.setAgendaItemCompleted(id, !currentCompleted)
    }

    suspend fun deleteCustomAgendaItem(id: Long) {
        dockDao.deleteAgendaItemById(id)
    }

    suspend fun sendAgentPrompt(
        prompt: String,
        endpointUrl: String,
        modelName: String,
        schedule: List<UnifiedScheduleItem>,
        notes: List<DockNoteEntity>,
        battery: BatteryTelemetry,
        weather: WeatherStationData?
    ): AgentResponseResult {
        dockDao.insertAgentMessage(
            AgentMessageEntity(
                role = "user",
                text = prompt.trim(),
                sourceBadge = "Dock Prompt"
            )
        )

        val result = localLlmBridge.runDockAgentQuery(
            userPrompt = prompt,
            endpointUrl = endpointUrl,
            modelName = modelName,
            schedule = schedule,
            notes = notes,
            battery = battery,
            weather = weather
        )

        if (result.autoDraftedNote != null) {
            dockDao.insertNote(result.autoDraftedNote)
        }

        dockDao.insertAgentMessage(
            AgentMessageEntity(
                role = "agent",
                text = result.replyText,
                sourceBadge = result.sourceBadge
            )
        )

        return result
    }

    suspend fun clearConversation() {
        dockDao.clearAgentMessages()
    }

    fun buildExternalShareContext(
        userPrompt: String,
        schedule: List<UnifiedScheduleItem>,
        notes: List<DockNoteEntity>,
        battery: BatteryTelemetry,
        weather: WeatherStationData?
    ): String {
        val ctx = localLlmBridge.buildDockContextPrompt(schedule, notes, battery, weather)
        return if (userPrompt.isBlank()) {
            "$ctx\nTask: Summarize my schedule, emails/notes, and top priorities for today."
        } else {
            "$ctx\nUser Query: $userPrompt"
        }
    }
}

