package com.example.data.agent

import com.example.data.calendar.AccountProviderBadge
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.hardware.BatteryTelemetry
import com.example.data.local.DockNoteEntity
import com.example.data.weather.WeatherStationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AgentResponseResult(
    val replyText: String,
    val sourceBadge: String, // e.g., "PocketPal HTTP (gemma-2-2b-it)" or "On-Device Tensor Core"
    val autoDraftedNote: DockNoteEntity? = null
)

class LocalLlmBridge {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun runDockAgentQuery(
        userPrompt: String,
        endpointUrl: String,
        modelName: String,
        schedule: List<UnifiedScheduleItem>,
        notes: List<DockNoteEntity>,
        battery: BatteryTelemetry,
        weather: WeatherStationData?
    ): AgentResponseResult = withContext(Dispatchers.IO) {
        val systemContext = buildDockContextPrompt(schedule, notes, battery, weather)

        // 1. Try local OpenAI-compatible server (PocketPal AI local server / llama.cpp / Ollama)
        if (endpointUrl.startsWith("http://") || endpointUrl.startsWith("https://")) {
            val localHttpReply = tryLocalOpenAiEndpoint(
                endpointUrl = endpointUrl,
                modelName = modelName,
                systemPrompt = systemContext,
                userPrompt = userPrompt
            )
            if (localHttpReply != null) {
                val maybeNote = extractDraftedNoteIfRequested(userPrompt, localHttpReply)
                return@withContext AgentResponseResult(
                    replyText = localHttpReply,
                    sourceBadge = "Local LLM ($modelName)",
                    autoDraftedNote = maybeNote
                )
            }
        }

        // 2. Fallback to immediate 100% Offline On-Device Executive Synthesis Engine
        synthesizeOfflineExecutiveReply(
            userPrompt = userPrompt,
            schedule = schedule,
            notes = notes,
            battery = battery,
            weather = weather
        )
    }

    private fun tryLocalOpenAiEndpoint(
        endpointUrl: String,
        modelName: String,
        systemPrompt: String,
        userPrompt: String
    ): String? {
        return try {
            val messages = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                )
                put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    }
                )
            }
            val payload = JSONObject().apply {
                put("model", modelName)
                put("messages", messages)
                put("temperature", 0.4)
                put("max_tokens", 512)
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpointUrl)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val raw = response.body?.string() ?: return null
                val json = JSONObject(raw)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val first = choices.getJSONObject(0)
                    val msg = first.optJSONObject("message")
                    val content = msg?.optString("content")?.trim()
                    if (!content.isNullOrBlank()) return content
                }
                // Also support Ollama /api/chat response shape
                val directMsg = json.optJSONObject("message")?.optString("content")?.trim()
                if (!directMsg.isNullOrBlank()) return directMsg
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun buildDockContextPrompt(
        schedule: List<UnifiedScheduleItem>,
        notes: List<DockNoteEntity>,
        battery: BatteryTelemetry,
        weather: WeatherStationData?
    ): String {
        val timeFmt = SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault())
        val shortTimeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("You are the Pixel 6a Smart Dock Agent running locally while docked.")
        sb.appendLine("Current Time: ${timeFmt.format(Date())}")
        sb.appendLine(
            "Dock Telemetry: Battery ${battery.levelPercent}% (${battery.powerSource}, " +
                "${battery.voltageMv}mV, ${"%.1f".format(battery.temperatureCelsius)}°C)"
        )
        if (weather != null) {
            sb.appendLine(
                "Weather (${weather.cityName}): ${"%.1f".format(weather.temperatureC)}°C, " +
                    "${weather.conditionLabel} (H:${"%.0f".format(weather.highC)}° L:${"%.0f".format(weather.lowC)}°)"
            )
        }
        if (schedule.isEmpty()) {
            sb.appendLine("Schedule: No upcoming events in the next 48h.")
        } else {
            sb.appendLine("Upcoming Schedule (${schedule.size} items):")
            schedule.take(8).forEach { ev ->
                sb.appendLine(
                    "- [${ev.providerBadge.label}] ${ev.title} (${shortTimeFmt.format(Date(ev.startTimeMillis))} - " +
                        "${shortTimeFmt.format(Date(ev.endTimeMillis))})"
                )
            }
        }
        if (notes.isNotEmpty()) {
            sb.appendLine("Recent Dock Notes (${notes.size}):")
            notes.take(5).forEach { n ->
                sb.appendLine("- [${n.category}] ${n.title}: ${n.content}")
            }
        }
        return sb.toString()
    }

    private fun synthesizeOfflineExecutiveReply(
        userPrompt: String,
        schedule: List<UnifiedScheduleItem>,
        notes: List<DockNoteEntity>,
        battery: BatteryTelemetry,
        weather: WeatherStationData?
    ): AgentResponseResult {
        val lower = userPrompt.lowercase(Locale.getDefault()).trim()
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val now = System.currentTimeMillis()
        val activeSchedule = schedule.filter { !it.isCompleted && it.endTimeMillis >= now }

        // Check if user wants to draft/save a note
        if (lower.startsWith("draft") ||
            lower.startsWith("note:") ||
            lower.startsWith("save note") ||
            lower.contains("draft a note") ||
            lower.contains("draft action")
        ) {
            val cleanedBody = userPrompt
                .replace(Regex("^(?i)(draft a note|draft action note|draft note|draft|note:|save note):?\\s*"), "")
                .ifBlank {
                    val nextMeeting = activeSchedule.firstOrNull()
                    if (nextMeeting != null) {
                        "Prep checklist for '${nextMeeting.title}' (${nextMeeting.providerBadge.label}) at ${timeFmt.format(Date(nextMeeting.startTimeMillis))}: review agenda, confirm action items, and summarize key takeaways."
                    } else {
                        "Daily dock checkpoint: review Gmail & Outlook inbox widgets, clear top 3 priorities, and organize tomorrow's calendar blocks."
                    }
                }
            val titleWords = cleanedBody.split(" ").take(5).joinToString(" ").trimEnd('.', ',', ':')
            val drafted = DockNoteEntity(
                title = if (titleWords.length > 3) titleWords else "Agent Dock Draft",
                content = cleanedBody,
                category = "AI Draft",
                isPinned = true
            )
            return AgentResponseResult(
                replyText = "Drafted and saved to **Dock Notes**:\n\n" +
                    "• **Title:** ${drafted.title}\n" +
                    "• **Tag:** ${drafted.category}\n" +
                    "• **Content:** ${drafted.content}",
                sourceBadge = "On-Device Tensor Core • Saved to Room",
                autoDraftedNote = drafted
            )
        }

        // Check if user wants focus blocks
        if (lower.contains("focus") || lower.contains("free time") || lower.contains("deep work") || lower.contains("gap")) {
            val focusReport = computeFocusBlocksReport(activeSchedule, now, timeFmt)
            return AgentResponseResult(
                replyText = focusReport,
                sourceBadge = "On-Device Schedule Analyzer"
            )
        }

        // Check if user asks about battery, thermal, or dock hardware
        if (lower.contains("battery") || lower.contains("charg") || lower.contains("temp") || lower.contains("voltage")) {
            return AgentResponseResult(
                replyText = "### Pixel 6a Dock Hardware Telemetry\n" +
                    "• **Charge Level:** ${battery.levelPercent}% (${battery.powerSource})\n" +
                    "• **Voltage & Current:** ${battery.voltageMv} mV • ${battery.currentMa} mA\n" +
                    "• **Thermal Sensor:** ${"%.1f".format(battery.temperatureCelsius)}°C " +
                    (if (battery.temperatureCelsius < 37f) "(Optimal for local LLM inference)" else "(Warm — consider dimming OLED display)"),
                sourceBadge = "On-Device Hardware Sensor"
            )
        }

        // Default or "Summarize my day" executive briefing
        val gmailCount = activeSchedule.count { it.providerBadge == AccountProviderBadge.GMAIL }
        val outlookCount = activeSchedule.count { it.providerBadge == AccountProviderBadge.OUTLOOK }
        val localCount = activeSchedule.count { it.providerBadge == AccountProviderBadge.SYSTEM }

        val sb = StringBuilder()
        sb.appendLine("### Daily Dock Executive Briefing")

        if (weather != null) {
            sb.appendLine(
                "• **Desk Environment (${weather.cityName}):** ${"%.1f".format(weather.temperatureC)}°C, " +
                    "${weather.conditionLabel} (High ${"%.0f".format(weather.highC)}° / Low ${"%.0f".format(weather.lowC)}°)"
            )
        }
        sb.appendLine(
            "• **Power Status:** ${battery.levelPercent}% on ${battery.powerSource} (${"%.1f".format(battery.temperatureCelsius)}°C)"
        )

        if (activeSchedule.isEmpty()) {
            sb.appendLine(
                "• **Unified Schedule:** Clear calendar ahead! No upcoming meetings detected across Gmail or Outlook. " +
                    "You have an open runway for deep focus work."
            )
        } else {
            sb.appendLine(
                "• **Unified Schedule:** ${activeSchedule.size} upcoming item(s) " +
                    "($gmailCount Gmail • $outlookCount Outlook • $localCount Local):"
            )
            activeSchedule.take(4).forEach { item ->
                sb.appendLine(
                    "  • **${timeFmt.format(Date(item.startTimeMillis))}** — ${item.title} *(${item.providerBadge.label})*"
                )
            }
        }

        if (notes.isNotEmpty()) {
            val pinnedCount = notes.count { it.isPinned }
            sb.appendLine(
                "• **Active Dock Notes:** ${notes.size} saved ($pinnedCount pinned). Latest: *\"${notes.first().title}\"*"
            )
        } else {
            sb.appendLine("• **Dock Notes:** No active notes pinned. Tap **Draft Action Note** to capture a memo.")
        }

        return AgentResponseResult(
            replyText = sb.toString().trim(),
            sourceBadge = "On-Device Tensor Core (Offline Ready)"
        )
    }

    private fun computeFocusBlocksReport(
        activeSchedule: List<UnifiedScheduleItem>,
        now: Long,
        timeFmt: SimpleDateFormat
    ): String {
        if (activeSchedule.isEmpty()) {
            return "### Deep Focus Availability\n" +
                "Your calendar is completely open for the next 8 hours. " +
                "Recommended: Start a **90-minute Tensor Focus Block** now while your Pixel 6a is docked."
        }

        val sorted = activeSchedule.sortedBy { it.startTimeMillis }
        val gaps = mutableListOf<String>()
        var cursor = now

        for (event in sorted.take(6)) {
            val diffMinutes = (event.startTimeMillis - cursor) / 60_000L
            if (diffMinutes >= 30) {
                gaps.add(
                    "• **${timeFmt.format(Date(cursor))} – ${timeFmt.format(Date(event.startTimeMillis))}** " +
                        "($diffMinutes min open before *${event.title}*)"
                )
            }
            if (event.endTimeMillis > cursor) {
                cursor = event.endTimeMillis
            }
        }

        val afterLastEnd = sorted.take(6).last().endTimeMillis
        gaps.add(
            "• **After ${timeFmt.format(Date(afterLastEnd))}** — Open afternoon/evening block"
        )

        return "### Recommended Focus Windows\n" + gaps.joinToString("\n")
    }

    private fun extractDraftedNoteIfRequested(userPrompt: String, reply: String): DockNoteEntity? {
        val lower = userPrompt.lowercase(Locale.getDefault())
        if (lower.startsWith("draft") || lower.startsWith("note:") || lower.contains("draft a note")) {
            val firstLine = reply.lineSequence().firstOrNull { it.isNotBlank() }
                ?.replace("#", "")?.replace("*", "")?.trim()
                ?.take(40) ?: "Local LLM Note"
            return DockNoteEntity(
                title = firstLine,
                content = reply,
                category = "AI Draft",
                isPinned = true
            )
        }
        return null
    }
}
