package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.filled.WbSunny
import com.example.ui.util.rememberDockHaptics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.calendar.AccountProviderBadge
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.local.AgentMessageEntity
import com.example.data.local.ConnectedAccountEntity
import com.example.data.local.DockAlarmEntity
import com.example.data.local.DockNoteEntity
import com.example.data.preferences.DockSettings
import com.example.data.weather.CityPreset
import com.example.data.weather.WeatherRepository
import com.example.ui.theme.ChargingEmerald
import com.example.ui.theme.GmailCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KilljoyGold
import com.example.ui.theme.OmenViolet
import com.example.ui.theme.OutlookBlue
import com.example.ui.theme.RadianiteCyan
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.ValorantCrimson
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DockControlCenterSheet(
    initialSection: Int = 0,
    settings: DockSettings,
    calendarPermissionGranted: Boolean,
    syncedAccounts: List<Pair<String, AccountProviderBadge>>,
    connectedAccounts: List<ConnectedAccountEntity> = emptyList(),
    alarms: List<DockAlarmEntity> = emptyList(),
    chronoState: ActiveChronoHudState = ActiveChronoHudState(),
    worldCitySearchResults: List<CityPreset> = WeatherRepository.CITY_PRESETS,
    scheduleItems: List<UnifiedScheduleItem>,
    selectedFilter: AccountProviderBadge?,
    notes: List<DockNoteEntity>,
    agentMessages: List<AgentMessageEntity>,
    isAgentThinking: Boolean,
    onDismiss: () -> Unit,
    onOpenChronoScreen: () -> Unit = {},
    onSelectClockStyle: (String) -> Unit,
    onSelectAgentTheme: (String) -> Unit,
    onToggle24Hour: () -> Unit,
    onToggleKeepAwake: () -> Unit,
    onToggleNightRed: () -> Unit,
    onToggleNightTimeOnly: () -> Unit = {},
    onUpdateNightTimeRange: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onToggleShowPortHub: () -> Unit,
    onToggleShowAgendaTicker: () -> Unit,
    onChangeDimmer: (Float) -> Unit,
    onSelectCity: (String) -> Unit,
    onSelectSecondaryCity: (String) -> Unit = {},
    onSaveDualCities: (String, String) -> Unit = { _, _ -> },
    onSearchWorldCities: (String) -> Unit = {},
    onSaveLlmConfig: (endpoint: String, model: String) -> Unit,
    onSendAgentPrompt: (String) -> Unit,
    onLaunchPocketPalApp: (String) -> Unit,
    onClearAgentHistory: () -> Unit,
    onSelectFilter: (AccountProviderBadge?) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRefreshCalendars: () -> Unit,
    onOpenAccountSyncSettings: () -> Unit,
    onOpenAddAccountSettings: () -> Unit,
    onAddConnectedAccount: (String, String, String) -> Unit = { _, _, _ -> },
    onToggleConnectedAccountSync: (ConnectedAccountEntity) -> Unit = {},
    onRemoveConnectedAccount: (ConnectedAccountEntity) -> Unit = {},
    onAddAlarm: (Int, Int, String) -> Unit = { _, _, _ -> },
    onToggleAlarm: (DockAlarmEntity) -> Unit = {},
    onDeleteAlarm: (Long) -> Unit = {},
    onTriggerAlarmNow: (DockAlarmEntity?) -> Unit = {},
    onStartTimer: (Int, String) -> Unit = { _, _ -> },
    onPauseResumeTimer: () -> Unit = {},
    onStartPandoraTimer: (Int, PandoraPhase) -> Unit = { _, _ -> },
    onPauseResumePandoraTimer: () -> Unit = {},
    onSnoozeChrono: (AiToyActivity) -> Unit = {},
    onStopChrono: (AiToyActivity) -> Unit = {},
    onToggleSunriseSimulation: () -> Unit = {},
    onPreviewSunriseDemo: () -> Unit = {},
    onToggleHaptics: () -> Unit = {},
    onInsertNativeCalendarEvent: () -> Unit,
    onOpenAddDockItemDialog: () -> Unit,
    onToggleItemCompleted: (UnifiedScheduleItem) -> Unit,
    onDeleteCustomItem: (UnifiedScheduleItem) -> Unit,
    onOpenGmailApp: () -> Unit,
    onOpenOutlookApp: () -> Unit,
    onOpenNewNoteDialog: () -> Unit,
    onTogglePinNote: (DockNoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF091018),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        DockControlCenterContent(
            initialSection = initialSection,
            isFullScreenPage = false,
            settings = settings,
            calendarPermissionGranted = calendarPermissionGranted,
            syncedAccounts = syncedAccounts,
            connectedAccounts = connectedAccounts,
            alarms = alarms,
            chronoState = chronoState,
            worldCitySearchResults = worldCitySearchResults,
            scheduleItems = scheduleItems,
            selectedFilter = selectedFilter,
            notes = notes,
            agentMessages = agentMessages,
            isAgentThinking = isAgentThinking,
            onBackOrDismiss = onDismiss,
            onOpenChronoScreen = {
                onDismiss()
                onOpenChronoScreen()
            },
            onToggleLandscapeMode = {},
            onSelectClockStyle = onSelectClockStyle,
            onSelectAgentTheme = onSelectAgentTheme,
            onToggle24Hour = onToggle24Hour,
            onToggleKeepAwake = onToggleKeepAwake,
            onToggleNightRed = onToggleNightRed,
            onToggleNightTimeOnly = onToggleNightTimeOnly,
            onUpdateNightTimeRange = onUpdateNightTimeRange,
            onToggleShowPortHub = onToggleShowPortHub,
            onToggleShowAgendaTicker = onToggleShowAgendaTicker,
            onChangeDimmer = onChangeDimmer,
            onSelectCity = onSelectCity,
            onSelectSecondaryCity = onSelectSecondaryCity,
            onSaveDualCities = onSaveDualCities,
            onSearchWorldCities = onSearchWorldCities,
            onSaveLlmConfig = onSaveLlmConfig,
            onSendAgentPrompt = onSendAgentPrompt,
            onLaunchPocketPalApp = onLaunchPocketPalApp,
            onClearAgentHistory = onClearAgentHistory,
            onSelectFilter = onSelectFilter,
            onRequestCalendarPermission = onRequestCalendarPermission,
            onRefreshCalendars = onRefreshCalendars,
            onOpenAccountSyncSettings = onOpenAccountSyncSettings,
            onOpenAddAccountSettings = onOpenAddAccountSettings,
            onAddConnectedAccount = onAddConnectedAccount,
            onToggleConnectedAccountSync = onToggleConnectedAccountSync,
            onRemoveConnectedAccount = onRemoveConnectedAccount,
            onAddAlarm = onAddAlarm,
            onToggleAlarm = onToggleAlarm,
            onDeleteAlarm = onDeleteAlarm,
            onTriggerAlarmNow = onTriggerAlarmNow,
            onStartTimer = onStartTimer,
            onPauseResumeTimer = onPauseResumeTimer,
            onStartPandoraTimer = onStartPandoraTimer,
            onPauseResumePandoraTimer = onPauseResumePandoraTimer,
            onSnoozeChrono = onSnoozeChrono,
            onStopChrono = onStopChrono,
            onToggleSunriseSimulation = onToggleSunriseSimulation,
            onPreviewSunriseDemo = onPreviewSunriseDemo,
            onToggleHaptics = onToggleHaptics,
            onInsertNativeCalendarEvent = onInsertNativeCalendarEvent,
            onOpenAddDockItemDialog = onOpenAddDockItemDialog,
            onToggleItemCompleted = onToggleItemCompleted,
            onDeleteCustomItem = onDeleteCustomItem,
            onOpenGmailApp = onOpenGmailApp,
            onOpenOutlookApp = onOpenOutlookApp,
            onOpenNewNoteDialog = onOpenNewNoteDialog,
            onTogglePinNote = onTogglePinNote,
            onDeleteNote = onDeleteNote
        )
    }
}

/**
 * Industry-Standard Full-Screen Settings & Control Hub Page accessed by swiping right from Clock.
 */
@Composable
fun DockSettingsStandByPage(
    settings: DockSettings,
    calendarPermissionGranted: Boolean,
    syncedAccounts: List<Pair<String, AccountProviderBadge>>,
    connectedAccounts: List<ConnectedAccountEntity> = emptyList(),
    alarms: List<DockAlarmEntity> = emptyList(),
    chronoState: ActiveChronoHudState = ActiveChronoHudState(),
    worldCitySearchResults: List<CityPreset> = WeatherRepository.CITY_PRESETS,
    scheduleItems: List<UnifiedScheduleItem>,
    selectedFilter: AccountProviderBadge?,
    notes: List<DockNoteEntity>,
    agentMessages: List<AgentMessageEntity>,
    isAgentThinking: Boolean,
    onBackToClock: () -> Unit,
    onOpenChronoScreen: () -> Unit = {},
    onToggleLandscapeMode: () -> Unit,
    onSelectClockStyle: (String) -> Unit,
    onSelectAgentTheme: (String) -> Unit,
    onToggle24Hour: () -> Unit,
    onToggleKeepAwake: () -> Unit,
    onToggleNightRed: () -> Unit,
    onToggleNightTimeOnly: () -> Unit = {},
    onUpdateNightTimeRange: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onToggleShowPortHub: () -> Unit,
    onToggleShowAgendaTicker: () -> Unit,
    onChangeDimmer: (Float) -> Unit,
    onSelectCity: (String) -> Unit,
    onSelectSecondaryCity: (String) -> Unit = {},
    onSaveDualCities: (String, String) -> Unit = { _, _ -> },
    onSearchWorldCities: (String) -> Unit = {},
    onSaveLlmConfig: (endpoint: String, model: String) -> Unit,
    onSendAgentPrompt: (String) -> Unit,
    onLaunchPocketPalApp: (String) -> Unit,
    onClearAgentHistory: () -> Unit,
    onSelectFilter: (AccountProviderBadge?) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRefreshCalendars: () -> Unit,
    onOpenAccountSyncSettings: () -> Unit,
    onOpenAddAccountSettings: () -> Unit,
    onAddConnectedAccount: (String, String, String) -> Unit = { _, _, _ -> },
    onToggleConnectedAccountSync: (ConnectedAccountEntity) -> Unit = {},
    onRemoveConnectedAccount: (ConnectedAccountEntity) -> Unit = {},
    onAddAlarm: (Int, Int, String) -> Unit = { _, _, _ -> },
    onToggleAlarm: (DockAlarmEntity) -> Unit = {},
    onDeleteAlarm: (Long) -> Unit = {},
    onTriggerAlarmNow: (DockAlarmEntity?) -> Unit = {},
    onStartTimer: (Int, String) -> Unit = { _, _ -> },
    onPauseResumeTimer: () -> Unit = {},
    onStartPandoraTimer: (Int, PandoraPhase) -> Unit = { _, _ -> },
    onPauseResumePandoraTimer: () -> Unit = {},
    onSnoozeChrono: (AiToyActivity) -> Unit = {},
    onStopChrono: (AiToyActivity) -> Unit = {},
    onToggleSunriseSimulation: () -> Unit = {},
    onPreviewSunriseDemo: () -> Unit = {},
    onToggleHaptics: () -> Unit = {},
    onInsertNativeCalendarEvent: () -> Unit,
    onOpenAddDockItemDialog: () -> Unit,
    onToggleItemCompleted: (UnifiedScheduleItem) -> Unit,
    onDeleteCustomItem: (UnifiedScheduleItem) -> Unit,
    onOpenGmailApp: () -> Unit,
    onOpenOutlookApp: () -> Unit,
    onOpenNewNoteDialog: () -> Unit,
    onTogglePinNote: (DockNoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    DockControlCenterContent(
        initialSection = 0,
        isFullScreenPage = true,
        settings = settings,
        calendarPermissionGranted = calendarPermissionGranted,
        syncedAccounts = syncedAccounts,
        connectedAccounts = connectedAccounts,
        alarms = alarms,
        chronoState = chronoState,
        worldCitySearchResults = worldCitySearchResults,
        scheduleItems = scheduleItems,
        selectedFilter = selectedFilter,
        notes = notes,
        agentMessages = agentMessages,
        isAgentThinking = isAgentThinking,
        onBackOrDismiss = onBackToClock,
        onOpenChronoScreen = onOpenChronoScreen,
        onToggleLandscapeMode = onToggleLandscapeMode,
        onSelectClockStyle = onSelectClockStyle,
        onSelectAgentTheme = onSelectAgentTheme,
        onToggle24Hour = onToggle24Hour,
        onToggleKeepAwake = onToggleKeepAwake,
        onToggleNightRed = onToggleNightRed,
        onToggleNightTimeOnly = onToggleNightTimeOnly,
        onUpdateNightTimeRange = onUpdateNightTimeRange,
        onToggleShowPortHub = onToggleShowPortHub,
        onToggleShowAgendaTicker = onToggleShowAgendaTicker,
        onChangeDimmer = onChangeDimmer,
        onSelectCity = onSelectCity,
        onSelectSecondaryCity = onSelectSecondaryCity,
        onSaveDualCities = onSaveDualCities,
        onSearchWorldCities = onSearchWorldCities,
        onSaveLlmConfig = onSaveLlmConfig,
        onSendAgentPrompt = onSendAgentPrompt,
        onLaunchPocketPalApp = onLaunchPocketPalApp,
        onClearAgentHistory = onClearAgentHistory,
        onSelectFilter = onSelectFilter,
        onRequestCalendarPermission = onRequestCalendarPermission,
        onRefreshCalendars = onRefreshCalendars,
        onOpenAccountSyncSettings = onOpenAccountSyncSettings,
        onOpenAddAccountSettings = onOpenAddAccountSettings,
        onAddConnectedAccount = onAddConnectedAccount,
        onToggleConnectedAccountSync = onToggleConnectedAccountSync,
        onRemoveConnectedAccount = onRemoveConnectedAccount,
        onAddAlarm = onAddAlarm,
        onToggleAlarm = onToggleAlarm,
        onDeleteAlarm = onDeleteAlarm,
        onTriggerAlarmNow = onTriggerAlarmNow,
        onStartTimer = onStartTimer,
        onPauseResumeTimer = onPauseResumeTimer,
        onStartPandoraTimer = onStartPandoraTimer,
        onPauseResumePandoraTimer = onPauseResumePandoraTimer,
        onSnoozeChrono = onSnoozeChrono,
        onStopChrono = onStopChrono,
        onToggleSunriseSimulation = onToggleSunriseSimulation,
        onPreviewSunriseDemo = onPreviewSunriseDemo,
        onToggleHaptics = onToggleHaptics,
        onInsertNativeCalendarEvent = onInsertNativeCalendarEvent,
        onOpenAddDockItemDialog = onOpenAddDockItemDialog,
        onToggleItemCompleted = onToggleItemCompleted,
        onDeleteCustomItem = onDeleteCustomItem,
        onOpenGmailApp = onOpenGmailApp,
        onOpenOutlookApp = onOpenOutlookApp,
        onOpenNewNoteDialog = onOpenNewNoteDialog,
        onTogglePinNote = onTogglePinNote,
        onDeleteNote = onDeleteNote
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DockControlCenterContent(
    initialSection: Int = 0,
    isFullScreenPage: Boolean = false,
    settings: DockSettings,
    calendarPermissionGranted: Boolean,
    syncedAccounts: List<Pair<String, AccountProviderBadge>>,
    connectedAccounts: List<ConnectedAccountEntity>,
    alarms: List<DockAlarmEntity>,
    chronoState: ActiveChronoHudState,
    worldCitySearchResults: List<CityPreset>,
    scheduleItems: List<UnifiedScheduleItem>,
    selectedFilter: AccountProviderBadge?,
    notes: List<DockNoteEntity>,
    agentMessages: List<AgentMessageEntity>,
    isAgentThinking: Boolean,
    onBackOrDismiss: () -> Unit,
    onOpenChronoScreen: () -> Unit = {},
    onToggleLandscapeMode: () -> Unit,
    onSelectClockStyle: (String) -> Unit,
    onSelectAgentTheme: (String) -> Unit,
    onToggle24Hour: () -> Unit,
    onToggleKeepAwake: () -> Unit,
    onToggleNightRed: () -> Unit,
    onToggleNightTimeOnly: () -> Unit,
    onUpdateNightTimeRange: (Int, Int, Int, Int) -> Unit,
    onToggleShowPortHub: () -> Unit,
    onToggleShowAgendaTicker: () -> Unit,
    onChangeDimmer: (Float) -> Unit,
    onSelectCity: (String) -> Unit,
    onSelectSecondaryCity: (String) -> Unit,
    onSaveDualCities: (String, String) -> Unit,
    onSearchWorldCities: (String) -> Unit,
    onSaveLlmConfig: (endpoint: String, model: String) -> Unit,
    onSendAgentPrompt: (String) -> Unit,
    onLaunchPocketPalApp: (String) -> Unit,
    onClearAgentHistory: () -> Unit,
    onSelectFilter: (AccountProviderBadge?) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRefreshCalendars: () -> Unit,
    onOpenAccountSyncSettings: () -> Unit,
    onOpenAddAccountSettings: () -> Unit,
    onAddConnectedAccount: (String, String, String) -> Unit,
    onToggleConnectedAccountSync: (ConnectedAccountEntity) -> Unit,
    onRemoveConnectedAccount: (ConnectedAccountEntity) -> Unit,
    onAddAlarm: (Int, Int, String) -> Unit,
    onToggleAlarm: (DockAlarmEntity) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onTriggerAlarmNow: (DockAlarmEntity?) -> Unit,
    onStartTimer: (Int, String) -> Unit,
    onPauseResumeTimer: () -> Unit,
    onStartPandoraTimer: (Int, PandoraPhase) -> Unit,
    onPauseResumePandoraTimer: () -> Unit,
    onSnoozeChrono: (AiToyActivity) -> Unit,
    onStopChrono: (AiToyActivity) -> Unit,
    onToggleSunriseSimulation: () -> Unit = {},
    onPreviewSunriseDemo: () -> Unit = {},
    onToggleHaptics: () -> Unit = {},
    onInsertNativeCalendarEvent: () -> Unit,
    onOpenAddDockItemDialog: () -> Unit,
    onToggleItemCompleted: (UnifiedScheduleItem) -> Unit,
    onDeleteCustomItem: (UnifiedScheduleItem) -> Unit,
    onOpenGmailApp: () -> Unit,
    onOpenOutlookApp: () -> Unit,
    onOpenNewNoteDialog: () -> Unit,
    onTogglePinNote: (DockNoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberDockHaptics(settings.hapticsEnabled)
    var activeSection by rememberSaveable { mutableIntStateOf(initialSection) }
    var endpoint by remember(settings.localLlmEndpoint) { mutableStateOf(settings.localLlmEndpoint) }
    var modelName by remember(settings.localLlmModelName) { mutableStateOf(settings.localLlmModelName) }
    var configSavedVisible by rememberSaveable { mutableStateOf(false) }
    var configSavedSummary by rememberSaveable { mutableStateOf("") }
    var worldCityQuery by rememberSaveable { mutableStateOf("") }
    var selectedWeatherSlot by rememberSaveable { mutableIntStateOf(0) } // 0 = Primary (Left), 1 = Secondary (Right)

    fun triggerSaveConfiguration() {
        haptics.success()
        onSaveLlmConfig(endpoint, modelName)
        onSaveDualCities(settings.cityName, settings.secondaryCityName)
        configSavedSummary =
            "Saved Dual Weather (${settings.cityName} & ${settings.secondaryCityName}), Night Schedule (%02d:%02d–%02d:%02d), and Display settings.".format(
                settings.nightStartHour,
                settings.nightStartMinute,
                settings.nightEndHour,
                settings.nightEndMinute
            )
        configSavedVisible = true
        Toast.makeText(
            context,
            "Configuration saved (${settings.cityName} & ${settings.secondaryCityName})",
            Toast.LENGTH_SHORT
        ).show()
    }

    val tabs = listOf(
        Triple(0, "Display & Weather", Icons.Default.Palette),
        Triple(1, "Agent AI", Icons.Default.Memory),
        Triple(2, "Accounts & Sync", Icons.Default.Sync),
        Triple(3, "Dock Notes", Icons.Default.StickyNote2)
    )

    BoxWithConstraints(
        modifier = (if (isFullScreenPage) {
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0B131E),
                            Color(0xFF05080E)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        }).testTag("dock_settings_page")
    ) {
        val isWideSettings = isFullScreenPage && maxWidth >= 620.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Industry-Standard Top App Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isFullScreenPage) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF121C28),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .testTag("btn_back_from_settings")
                        ) {
                            IconButton(
                                onClick = onBackOrDismiss,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Clock",
                                    tint = RadianiteCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            text = "Settings",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "StandBy Display • Night Mode • Dual Weather • Accounts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Launch Pill for Dedicated Alarm, Timer & Focus Screen
                    OutlinedButton(
                        onClick = onOpenChronoScreen,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_open_chrono_screen_from_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Open Alarm, Timer & Focus Screen",
                            tint = KilljoyGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Alarms & Focus",
                            style = MaterialTheme.typography.labelSmall,
                            color = KilljoyGold
                        )
                    }

                    if (isFullScreenPage) {
                        IconButton(
                            onClick = onToggleLandscapeMode,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (settings.forceLandscapeMode) {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
                                    } else {
                                        Color(0xFF121C28)
                                    }
                                )
                                .testTag("toggle_landscape_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Toggle Landscape / Portrait Orientation",
                                tint = if (settings.forceLandscapeMode) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onBackOrDismiss,
                            modifier = Modifier.testTag("close_settings_sheet_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isWideSettings) {
                // Canonical 2-Pane Master-Detail Layout for Landscape / Wide Screens
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Category Navigation Rail / Sidebar
                    Column(
                        modifier = Modifier
                            .width(215.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0C141F))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "CATEGORIES",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            tabs.forEach { (idx, label, icon) ->
                                val selected = activeSection == idx
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    } else {
                                        Color.Transparent
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { activeSection = idx }
                                        .testTag("settings_tab_$idx")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = label,
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Active Status Summary Box at bottom of Sidebar
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF101B29),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "ACTIVE DOCK PROFILE",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RadianiteCyan
                                )
                                Text(
                                    text = "${settings.cityName} • ${settings.secondaryCityName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Night 20ft: %02d:%02d–%02d:%02d (%s)".format(
                                        settings.nightStartHour,
                                        settings.nightStartMinute,
                                        settings.nightEndHour,
                                        settings.nightEndMinute,
                                        if (settings.nightTimeOnlyEnabled) "ON" else "OFF"
                                    ),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Right Scrollable Detail Pane
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SettingsActiveSectionBody(
                            activeSection = activeSection,
                            settings = settings,
                            endpoint = endpoint,
                            onEndpointChange = { endpoint = it },
                            modelName = modelName,
                            onModelNameChange = { modelName = it },
                            configSavedVisible = configSavedVisible,
                            configSavedSummary = configSavedSummary,
                            worldCityQuery = worldCityQuery,
                            onWorldCityQueryChange = { q ->
                                worldCityQuery = q
                                onSearchWorldCities(q)
                            },
                            selectedWeatherSlot = selectedWeatherSlot,
                            onSelectWeatherSlot = { slot ->
                                selectedWeatherSlot = slot
                                worldCityQuery = ""
                                onSearchWorldCities("")
                            },
                            worldCitySearchResults = worldCitySearchResults,
                            calendarPermissionGranted = calendarPermissionGranted,
                            syncedAccounts = syncedAccounts,
                            connectedAccounts = connectedAccounts,
                            alarms = alarms,
                            chronoState = chronoState,
                            scheduleItems = scheduleItems,
                            selectedFilter = selectedFilter,
                            notes = notes,
                            agentMessages = agentMessages,
                            isAgentThinking = isAgentThinking,
                            onToggleLandscapeMode = onToggleLandscapeMode,
                            onSelectClockStyle = onSelectClockStyle,
                            onSelectAgentTheme = onSelectAgentTheme,
                            onToggle24Hour = onToggle24Hour,
                            onToggleKeepAwake = onToggleKeepAwake,
                            onToggleNightRed = onToggleNightRed,
                            onToggleNightTimeOnly = onToggleNightTimeOnly,
                            onUpdateNightTimeRange = onUpdateNightTimeRange,
                            onToggleShowPortHub = onToggleShowPortHub,
                            onToggleShowAgendaTicker = onToggleShowAgendaTicker,
                            onChangeDimmer = onChangeDimmer,
                            onSelectCity = onSelectCity,
                            onSelectSecondaryCity = onSelectSecondaryCity,
                            onSaveDualCities = onSaveDualCities,
                            onTriggerSaveConfiguration = ::triggerSaveConfiguration,
                            onSendAgentPrompt = onSendAgentPrompt,
                            onLaunchPocketPalApp = onLaunchPocketPalApp,
                            onOpenLlmSettings = { activeSection = 0 },
                            onClearAgentHistory = onClearAgentHistory,
                            onSelectFilter = onSelectFilter,
                            onRequestCalendarPermission = onRequestCalendarPermission,
                            onRefreshCalendars = onRefreshCalendars,
                            onOpenAccountSyncSettings = onOpenAccountSyncSettings,
                            onOpenAddAccountSettings = onOpenAddAccountSettings,
                            onAddConnectedAccount = onAddConnectedAccount,
                            onToggleConnectedAccountSync = onToggleConnectedAccountSync,
                            onRemoveConnectedAccount = onRemoveConnectedAccount,
                            onAddAlarm = onAddAlarm,
                            onToggleAlarm = onToggleAlarm,
                            onDeleteAlarm = onDeleteAlarm,
                            onTriggerAlarmNow = onTriggerAlarmNow,
                            onStartTimer = onStartTimer,
                            onPauseResumeTimer = onPauseResumeTimer,
                            onStartPandoraTimer = onStartPandoraTimer,
                            onPauseResumePandoraTimer = onPauseResumePandoraTimer,
                            onSnoozeChrono = onSnoozeChrono,
                            onStopChrono = onStopChrono,
                            onToggleSunriseSimulation = onToggleSunriseSimulation,
                            onPreviewSunriseDemo = onPreviewSunriseDemo,
                            onToggleHaptics = onToggleHaptics,
                            onInsertNativeCalendarEvent = onInsertNativeCalendarEvent,
                            onOpenAddDockItemDialog = onOpenAddDockItemDialog,
                            onToggleItemCompleted = onToggleItemCompleted,
                            onDeleteCustomItem = onDeleteCustomItem,
                            onOpenGmailApp = onOpenGmailApp,
                            onOpenOutlookApp = onOpenOutlookApp,
                            onOpenNewNoteDialog = onOpenNewNoteDialog,
                            onTogglePinNote = onTogglePinNote,
                            onDeleteNote = onDeleteNote
                        )
                    }
                }
            } else {
                // Portrait / Compact Segmented TabRow + Scrollable Grouped Cards
                TabRow(
                    selectedTabIndex = activeSection,
                    containerColor = Color(0xFF0E1723),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    tabs.forEach { (idx, label, icon) ->
                        Tab(
                            selected = activeSection == idx,
                            onClick = { activeSection = idx },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label.substringBefore(" "),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            },
                            modifier = Modifier.testTag("settings_tab_$idx")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = isFullScreenPage)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SettingsActiveSectionBody(
                        activeSection = activeSection,
                        settings = settings,
                        endpoint = endpoint,
                        onEndpointChange = { endpoint = it },
                        modelName = modelName,
                        onModelNameChange = { modelName = it },
                        configSavedVisible = configSavedVisible,
                        configSavedSummary = configSavedSummary,
                        worldCityQuery = worldCityQuery,
                        onWorldCityQueryChange = { q ->
                            worldCityQuery = q
                            onSearchWorldCities(q)
                        },
                        selectedWeatherSlot = selectedWeatherSlot,
                        onSelectWeatherSlot = { slot ->
                            selectedWeatherSlot = slot
                            worldCityQuery = ""
                            onSearchWorldCities("")
                        },
                        worldCitySearchResults = worldCitySearchResults,
                        calendarPermissionGranted = calendarPermissionGranted,
                        syncedAccounts = syncedAccounts,
                        connectedAccounts = connectedAccounts,
                        alarms = alarms,
                        chronoState = chronoState,
                        scheduleItems = scheduleItems,
                        selectedFilter = selectedFilter,
                        notes = notes,
                        agentMessages = agentMessages,
                        isAgentThinking = isAgentThinking,
                        onToggleLandscapeMode = onToggleLandscapeMode,
                        onSelectClockStyle = onSelectClockStyle,
                        onSelectAgentTheme = onSelectAgentTheme,
                        onToggle24Hour = onToggle24Hour,
                        onToggleKeepAwake = onToggleKeepAwake,
                        onToggleNightRed = onToggleNightRed,
                        onToggleNightTimeOnly = onToggleNightTimeOnly,
                        onUpdateNightTimeRange = onUpdateNightTimeRange,
                        onToggleShowPortHub = onToggleShowPortHub,
                        onToggleShowAgendaTicker = onToggleShowAgendaTicker,
                        onChangeDimmer = onChangeDimmer,
                        onSelectCity = onSelectCity,
                        onSelectSecondaryCity = onSelectSecondaryCity,
                        onSaveDualCities = onSaveDualCities,
                        onTriggerSaveConfiguration = ::triggerSaveConfiguration,
                        onSendAgentPrompt = onSendAgentPrompt,
                        onLaunchPocketPalApp = onLaunchPocketPalApp,
                        onOpenLlmSettings = { activeSection = 0 },
                        onClearAgentHistory = onClearAgentHistory,
                        onSelectFilter = onSelectFilter,
                        onRequestCalendarPermission = onRequestCalendarPermission,
                        onRefreshCalendars = onRefreshCalendars,
                        onOpenAccountSyncSettings = onOpenAccountSyncSettings,
                        onOpenAddAccountSettings = onOpenAddAccountSettings,
                        onAddConnectedAccount = onAddConnectedAccount,
                        onToggleConnectedAccountSync = onToggleConnectedAccountSync,
                        onRemoveConnectedAccount = onRemoveConnectedAccount,
                        onAddAlarm = onAddAlarm,
                        onToggleAlarm = onToggleAlarm,
                        onDeleteAlarm = onDeleteAlarm,
                        onTriggerAlarmNow = onTriggerAlarmNow,
                        onStartTimer = onStartTimer,
                        onPauseResumeTimer = onPauseResumeTimer,
                        onStartPandoraTimer = onStartPandoraTimer,
                        onPauseResumePandoraTimer = onPauseResumePandoraTimer,
                        onSnoozeChrono = onSnoozeChrono,
                        onStopChrono = onStopChrono,
                        onToggleSunriseSimulation = onToggleSunriseSimulation,
                        onPreviewSunriseDemo = onPreviewSunriseDemo,
                        onToggleHaptics = onToggleHaptics,
                        onInsertNativeCalendarEvent = onInsertNativeCalendarEvent,
                        onOpenAddDockItemDialog = onOpenAddDockItemDialog,
                        onToggleItemCompleted = onToggleItemCompleted,
                        onDeleteCustomItem = onDeleteCustomItem,
                        onOpenGmailApp = onOpenGmailApp,
                        onOpenOutlookApp = onOpenOutlookApp,
                        onOpenNewNoteDialog = onOpenNewNoteDialog,
                        onTogglePinNote = onTogglePinNote,
                        onDeleteNote = onDeleteNote
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsActiveSectionBody(
    activeSection: Int,
    settings: DockSettings,
    endpoint: String,
    onEndpointChange: (String) -> Unit,
    modelName: String,
    onModelNameChange: (String) -> Unit,
    configSavedVisible: Boolean,
    configSavedSummary: String,
    worldCityQuery: String,
    onWorldCityQueryChange: (String) -> Unit,
    selectedWeatherSlot: Int,
    onSelectWeatherSlot: (Int) -> Unit,
    worldCitySearchResults: List<CityPreset>,
    calendarPermissionGranted: Boolean,
    syncedAccounts: List<Pair<String, AccountProviderBadge>>,
    connectedAccounts: List<ConnectedAccountEntity>,
    alarms: List<DockAlarmEntity>,
    chronoState: ActiveChronoHudState,
    scheduleItems: List<UnifiedScheduleItem>,
    selectedFilter: AccountProviderBadge?,
    notes: List<DockNoteEntity>,
    agentMessages: List<AgentMessageEntity>,
    isAgentThinking: Boolean,
    onToggleLandscapeMode: () -> Unit,
    onSelectClockStyle: (String) -> Unit,
    onSelectAgentTheme: (String) -> Unit,
    onToggle24Hour: () -> Unit,
    onToggleKeepAwake: () -> Unit,
    onToggleNightRed: () -> Unit,
    onToggleNightTimeOnly: () -> Unit,
    onUpdateNightTimeRange: (Int, Int, Int, Int) -> Unit,
    onToggleShowPortHub: () -> Unit,
    onToggleShowAgendaTicker: () -> Unit,
    onChangeDimmer: (Float) -> Unit,
    onSelectCity: (String) -> Unit,
    onSelectSecondaryCity: (String) -> Unit,
    onSaveDualCities: (String, String) -> Unit,
    onTriggerSaveConfiguration: () -> Unit,
    onSendAgentPrompt: (String) -> Unit,
    onLaunchPocketPalApp: (String) -> Unit,
    onOpenLlmSettings: () -> Unit,
    onClearAgentHistory: () -> Unit,
    onSelectFilter: (AccountProviderBadge?) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRefreshCalendars: () -> Unit,
    onOpenAccountSyncSettings: () -> Unit,
    onOpenAddAccountSettings: () -> Unit,
    onAddConnectedAccount: (String, String, String) -> Unit,
    onToggleConnectedAccountSync: (ConnectedAccountEntity) -> Unit,
    onRemoveConnectedAccount: (ConnectedAccountEntity) -> Unit,
    onAddAlarm: (Int, Int, String) -> Unit,
    onToggleAlarm: (DockAlarmEntity) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onTriggerAlarmNow: (DockAlarmEntity?) -> Unit,
    onStartTimer: (Int, String) -> Unit,
    onPauseResumeTimer: () -> Unit,
    onStartPandoraTimer: (Int, PandoraPhase) -> Unit,
    onPauseResumePandoraTimer: () -> Unit,
    onSnoozeChrono: (AiToyActivity) -> Unit,
    onStopChrono: (AiToyActivity) -> Unit,
    onToggleSunriseSimulation: () -> Unit,
    onPreviewSunriseDemo: () -> Unit,
    onToggleHaptics: () -> Unit,
    onInsertNativeCalendarEvent: () -> Unit,
    onOpenAddDockItemDialog: () -> Unit,
    onToggleItemCompleted: (UnifiedScheduleItem) -> Unit,
    onDeleteCustomItem: (UnifiedScheduleItem) -> Unit,
    onOpenGmailApp: () -> Unit,
    onOpenOutlookApp: () -> Unit,
    onOpenNewNoteDialog: () -> Unit,
    onTogglePinNote: (DockNoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    when (activeSection) {
        0 -> {
            // GROUP 1: APPEARANCE, CLOCK FACE & THEME
            SettingsGroupCard(
                title = "Appearance & Clock Face",
                subtitle = "Customize your StandBy typography, theme accent, and OLED luminance",
                icon = Icons.Default.Palette,
                accentColor = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = "CLOCK DISPLAY ARCHITECTURE",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        Triple("NANO", "Nano Bot HUD", "Split Flip + AI Stage"),
                        Triple("FLIP", "Flip Clock", " Mechanical Cards"),
                        Triple("MATRIX", "240Hz Matrix", "Cyber Digital Readout"),
                        Triple("CHRONO", "Prime Dial", "Analog Sweep Ring")
                    ).forEach { (styleKey, label, desc) ->
                        val selected = settings.clockStyle == styleKey
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectClockStyle(styleKey) },
                            label = {
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RadianiteCyan.copy(alpha = 0.2f),
                                selectedLabelColor = RadianiteCyan
                            ),
                            modifier = Modifier.testTag("clock_style_${styleKey.lowercase()}")
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Text(
                    text = "INTERFACE ACCENT PALETTE",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("VALORANT", "Crimson Protocol", ValorantCrimson),
                        Triple("JETT", "Radiant Cyan", RadianiteCyan),
                        Triple("KILLJOY", "Solar Gold", KilljoyGold),
                        Triple("OMEN", "Void Violet", OmenViolet)
                    ).forEach { (themeKey, label, swatchColor) ->
                        FilterChip(
                            selected = settings.agentTheme == themeKey,
                            onClick = { onSelectAgentTheme(themeKey) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                )
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = swatchColor.copy(alpha = 0.22f),
                                selectedLabelColor = swatchColor
                            )
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // OLED Luminance Slider Row
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Brightness6,
                                contentDescription = null,
                                tint = RadianiteCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "OLED Panel Luminance",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RadianiteCyan.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "${(settings.brightnessDimmer * 100).toInt()}%",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = RadianiteCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Slider(
                        value = settings.brightnessDimmer,
                        onValueChange = onChangeDimmer,
                        valueRange = 0.25f..1.0f
                    )
                }
            }

            // GROUP 2: NIGHT TIME-ONLY FULL SCREEN (20-FT BEDSIDE MODE)
            SettingsGroupCard(
                title = "Night Time-Only Full Screen (20-ft Mode)",
                subtitle = "Hide animations and weather cards during quiet hours to show an ultra-bold clock visible from 20 feet",
                icon = Icons.Default.Bedtime,
                accentColor = RadianiteCyan,
                modifier = Modifier.testTag("night_time_range_card")
            ) {
                IndustrySettingSwitchRow(
                    icon = Icons.Default.WatchLater,
                    iconTint = RadianiteCyan,
                    title = "Night Time-Only Full Screen (Hide Animation & Weather)",
                    subtitle = "Automatically switch to giant 20-ft clock during your scheduled window",
                    checked = settings.nightTimeOnlyEnabled,
                    onCheckedChange = { onToggleNightTimeOnly() }
                )

                val startFormatted = "%02d:%02d".format(settings.nightStartHour, settings.nightStartMinute)
                val endFormatted = "%02d:%02d".format(settings.nightEndHour, settings.nightEndMinute)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0A111A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (settings.nightTimeOnlyEnabled) {
                                RadianiteCyan.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Window: $startFormatted – $endFormatted",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (settings.nightTimeOnlyEnabled) RadianiteCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (settings.nightTimeOnlyEnabled) {
                                    ChargingEmerald.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = if (settings.nightTimeOnlyEnabled) "ENABLED" else "OFF",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (settings.nightTimeOnlyEnabled) ChargingEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        NightTimeEndpointAdjusterRow(
                            label = "Start Time",
                            hour = settings.nightStartHour,
                            minute = settings.nightStartMinute,
                            onAdjustMinutes = { deltaMins ->
                                val total = ((settings.nightStartHour * 60 + settings.nightStartMinute + deltaMins) % 1440 + 1440) % 1440
                                onUpdateNightTimeRange(
                                    total / 60,
                                    total % 60,
                                    settings.nightEndHour,
                                    settings.nightEndMinute
                                )
                            },
                            testTagPrefix = "night_start"
                        )

                        NightTimeEndpointAdjusterRow(
                            label = "End Time",
                            hour = settings.nightEndHour,
                            minute = settings.nightEndMinute,
                            onAdjustMinutes = { deltaMins ->
                                val total = ((settings.nightEndHour * 60 + settings.nightEndMinute + deltaMins) % 1440 + 1440) % 1440
                                onUpdateNightTimeRange(
                                    settings.nightStartHour,
                                    settings.nightStartMinute,
                                    total / 60,
                                    total % 60
                                )
                            },
                            testTagPrefix = "night_end"
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("22:00 – 07:00", 22 to 0, 7 to 0),
                                Triple("23:00 – 06:00", 23 to 0, 6 to 0),
                                Triple("00:00 – 08:00", 0 to 0, 8 to 0),
                                Triple("Always On (24h)", 0 to 0, 0 to 0)
                            ).forEach { (presetLabel, startPair, endPair) ->
                                val isSelected = settings.nightStartHour == startPair.first &&
                                    settings.nightStartMinute == startPair.second &&
                                    settings.nightEndHour == endPair.first &&
                                    settings.nightEndMinute == endPair.second
                                FilterChip(
                                    selected = isSelected && settings.nightTimeOnlyEnabled,
                                    onClick = {
                                        onUpdateNightTimeRange(
                                            startPair.first,
                                            startPair.second,
                                            endPair.first,
                                            endPair.second
                                        )
                                        if (!settings.nightTimeOnlyEnabled) {
                                            onToggleNightTimeOnly()
                                        }
                                    },
                                    label = { Text(presetLabel, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            // GROUP 3: COMPACT DUAL WEATHER STATIONS (SINGLE ACTIVE SLOT)
            SettingsGroupCard(
                title = "Dual Weather Stations",
                subtitle = "Select Primary (Left) or Secondary (Right) to search and assign any city worldwide",
                icon = Icons.Default.Public,
                accentColor = KilljoyGold,
                modifier = Modifier.testTag("dual_weather_city_selector_card"),
                trailingHeaderAction = {
                    OutlinedButton(
                        onClick = {
                            onSaveDualCities(settings.secondaryCityName, settings.cityName)
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("swap_dual_cities_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Swap Left and Right Weather Cities",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Swap", style = MaterialTheme.typography.labelSmall)
                    }
                }
            ) {
                val isPrimarySelected = selectedWeatherSlot == 0
                val activeAccentColor = if (isPrimarySelected) RadianiteCyan else KilljoyGold
                val activeStationLabel = if (isPrimarySelected) "Primary (Left)" else "Secondary (Right)"
                val activeCurrentCity = if (isPrimarySelected) settings.cityName else settings.secondaryCityName

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isPrimarySelected,
                        onClick = { onSelectWeatherSlot(0) },
                        label = {
                            Text(
                                text = "1. Primary: ${settings.cityName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isPrimarySelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RadianiteCyan.copy(alpha = 0.22f),
                            selectedLabelColor = RadianiteCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_primary_weather_slot")
                    )

                    FilterChip(
                        selected = !isPrimarySelected,
                        onClick = { onSelectWeatherSlot(1) },
                        label = {
                            Text(
                                text = "2. Secondary: ${settings.secondaryCityName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (!isPrimarySelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KilljoyGold.copy(alpha = 0.22f),
                            selectedLabelColor = KilljoyGold
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_secondary_weather_slot")
                    )
                }

                // Single Search & Add row for the currently selected station ONLY
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = worldCityQuery,
                        onValueChange = onWorldCityQueryChange,
                        label = {
                            Text(
                                "Search & add city for $activeStationLabel",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search $activeStationLabel City",
                                tint = activeAccentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (worldCityQuery.isNotBlank()) {
                            {
                                IconButton(onClick = { onWorldCityQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear city search",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("world_city_search_input")
                    )

                    if (worldCityQuery.isNotBlank()) {
                        Button(
                            onClick = {
                                val cleanCity = worldCityQuery.trim()
                                if (isPrimarySelected) {
                                    onSelectCity(cleanCity)
                                } else {
                                    onSelectSecondaryCity(cleanCity)
                                }
                                onWorldCityQueryChange("")
                            },
                            modifier = Modifier.testTag(
                                if (isPrimarySelected) "btn_assign_custom_city_left" else "btn_assign_custom_city_right"
                            )
                        ) {
                            Text(
                                text = if (isPrimarySelected) "Add Primary" else "Add Secondary",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                val compactCities = remember(worldCityQuery, worldCitySearchResults, activeCurrentCity) {
                    if (worldCityQuery.isNotBlank()) {
                        worldCitySearchResults.take(8)
                    } else {
                        val currentPreset = WeatherRepository.findCityPreset(
                            activeCurrentCity,
                            WeatherRepository.POITIERS_PRESET
                        )
                        (listOf(currentPreset) + WeatherRepository.CITY_PRESETS.take(6))
                            .distinctBy { it.name.lowercase() }
                            .take(6)
                    }
                }

                Text(
                    text = if (worldCityQuery.isNotBlank()) {
                        "Select match for $activeStationLabel (${compactCities.size} shown):"
                    } else {
                        "Quick pick for $activeStationLabel (search above for any world city):"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = activeAccentColor
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    compactCities.forEach { preset ->
                        val isCurrentCity = activeCurrentCity.equals(preset.name, ignoreCase = true)
                        val tagPrefix = if (isPrimarySelected) "primary_city_" else "secondary_city_"
                        FilterChip(
                            selected = isCurrentCity,
                            onClick = {
                                if (isPrimarySelected) {
                                    onSelectCity(preset.name)
                                } else {
                                    onSelectSecondaryCity(preset.name)
                                }
                                if (worldCityQuery.isNotBlank()) {
                                    onWorldCityQueryChange("")
                                }
                            },
                            label = {
                                Text(
                                    text = if (preset.countryCode == "WORLD") {
                                        preset.name
                                    } else {
                                        "${preset.name} (${preset.countryCode})"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = activeAccentColor.copy(alpha = 0.22f),
                                selectedLabelColor = activeAccentColor
                            ),
                            modifier = Modifier.testTag("$tagPrefix${preset.name.lowercase().replace(" ", "_")}")
                        )
                    }
                }
            }

            // GROUP 4: STANDBY DISPLAY & HARDWARE BEHAVIOR
            SettingsGroupCard(
                title = "StandBy Display & Telemetry",
                subtitle = "Configure orientation lock, time format, wake lock, and port telemetry",
                icon = Icons.Default.Tune,
                accentColor = ChargingEmerald
            ) {
                IndustrySettingSwitchRow(
                    icon = Icons.Default.ScreenRotation,
                    iconTint = RadianiteCyan,
                    title = "Force Landscape Dock Orientation",
                    subtitle = "Lock widescreen StandBy dock layout even when portrait",
                    checked = settings.forceLandscapeMode,
                    onCheckedChange = { onToggleLandscapeMode() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                IndustrySettingSwitchRow(
                    icon = Icons.Default.AccessTime,
                    iconTint = KilljoyGold,
                    title = "24-Hour Military Clock",
                    subtitle = "Display hours in 00:00 – 23:59 format across all screens",
                    checked = settings.use24HourFormat,
                    onCheckedChange = { onToggle24Hour() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                IndustrySettingSwitchRow(
                    icon = Icons.Default.Lock,
                    iconTint = ChargingEmerald,
                    title = "Keep Screen Awake (Dock Lock)",
                    subtitle = "Prevent Android from dimming or locking while docked",
                    checked = settings.keepScreenAwake,
                    onCheckedChange = { onToggleKeepAwake() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                IndustrySettingSwitchRow(
                    icon = Icons.Default.DarkMode,
                    iconTint = ValorantCrimson,
                    title = "OLED Night Red Bedside Mode",
                    subtitle = "Shift UI palette to low-glare crimson monochrome for dark rooms",
                    checked = settings.nightRedMode,
                    onCheckedChange = { onToggleNightRed() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                IndustrySettingSwitchRow(
                    icon = Icons.Default.Bolt,
                    iconTint = RadianiteCyan,
                    title = "Show 10-in-1 Port & Power Flow Bar",
                    subtitle = "Display live USB-C PD wattage, voltage & thermal telemetry",
                    checked = settings.showPortHubTelemetry,
                    onCheckedChange = { onToggleShowPortHub() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                IndustrySettingSwitchRow(
                    icon = Icons.Default.CalendarMonth,
                    iconTint = OmenViolet,
                    title = "Show Next Event & Agent Ticker on Home",
                    subtitle = "Display compact calendar & AI briefing bar on the dock screen",
                    checked = settings.showAgendaTicker,
                    onCheckedChange = { onToggleShowAgendaTicker() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                IndustrySettingSwitchRow(
                    icon = Icons.Default.Vibration,
                    iconTint = ChargingEmerald,
                    title = "Subtle Vibration Haptic Feedback",
                    subtitle = "Delivers tactile micro-pulses when swiping between pages, toggling schedule buttons, or setting alarms",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { onToggleHaptics() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                IndustrySettingSwitchRow(
                    icon = Icons.Default.WbSunny,
                    iconTint = KilljoyGold,
                    title = "Sunrise Dawn Wake-Up Simulation (30 min)",
                    subtitle = "Gradually increases screen brightness and shifts color temperature 30 minutes before your next alarm triggers",
                    checked = settings.sunriseSimulationEnabled,
                    onCheckedChange = { onToggleSunriseSimulation() }
                )

                if (settings.sunriseSimulationEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onPreviewSunriseDemo,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_preview_sunrise_in_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = KilljoyGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Preview Sunrise (30m Demo)",
                                color = KilljoyGold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // GROUP 5: LOCAL LLM ENGINE & SAVE CONFIGURATION
            SettingsGroupCard(
                title = "On-Device AI Runtime (PocketPal / llama.cpp)",
                subtitle = "Configure local OpenAI-compatible inference endpoint and save dock profile",
                icon = Icons.Default.Memory,
                accentColor = OmenViolet
            ) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = onEndpointChange,
                    label = { Text("Endpoint URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = onModelNameChange,
                    label = { Text("Local Model ID (e.g. gemma-2-2b-it)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (configSavedVisible) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ChargingEmerald.copy(alpha = 0.16f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = ChargingEmerald.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .testTag("config_saved_confirmation_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Configuration Saved",
                                tint = ChargingEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CONFIGURATION SAVED",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ChargingEmerald
                                )
                                Text(
                                    text = configSavedSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onTriggerSaveConfiguration,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_dock_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Configuration",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        1 -> {
            AgentCorePanel(
                modelName = settings.localLlmModelName,
                endpointUrl = settings.localLlmEndpoint,
                isThinking = isAgentThinking,
                messages = agentMessages,
                onSendPrompt = onSendAgentPrompt,
                onLaunchPocketPalApp = onLaunchPocketPalApp,
                onOpenLlmSettings = onOpenLlmSettings,
                onClearHistory = onClearAgentHistory
            )
        }

        2 -> {
            SyncBridgeAndWidgetsPanel(
                calendarPermissionGranted = calendarPermissionGranted,
                syncedAccounts = syncedAccounts,
                connectedAccounts = connectedAccounts,
                alarms = alarms,
                chronoState = chronoState,
                scheduleItems = scheduleItems,
                selectedFilter = selectedFilter,
                onSelectFilter = onSelectFilter,
                onRequestCalendarPermission = onRequestCalendarPermission,
                onRefreshCalendars = onRefreshCalendars,
                onOpenAccountSyncSettings = onOpenAccountSyncSettings,
                onOpenAddAccountSettings = onOpenAddAccountSettings,
                onAddConnectedAccount = onAddConnectedAccount,
                onToggleConnectedAccountSync = onToggleConnectedAccountSync,
                onRemoveConnectedAccount = onRemoveConnectedAccount,
                onAddAlarm = onAddAlarm,
                onToggleAlarm = onToggleAlarm,
                onDeleteAlarm = onDeleteAlarm,
                onTriggerAlarmNow = onTriggerAlarmNow,
                onStartTimer = onStartTimer,
                onPauseResumeTimer = onPauseResumeTimer,
                onStartPandoraTimer = onStartPandoraTimer,
                onPauseResumePandoraTimer = onPauseResumePandoraTimer,
                onSnoozeChrono = onSnoozeChrono,
                onStopChrono = onStopChrono,
                onInsertNativeCalendarEvent = onInsertNativeCalendarEvent,
                onOpenAddDockItemDialog = onOpenAddDockItemDialog,
                onToggleItemCompleted = onToggleItemCompleted,
                onDeleteCustomItem = onDeleteCustomItem,
                onOpenGmailApp = onOpenGmailApp,
                onOpenOutlookApp = onOpenOutlookApp
            )
        }

        3 -> {
            DockNotesPanel(
                notes = notes,
                onOpenNewNoteDialog = onOpenNewNoteDialog,
                onTogglePin = onTogglePinNote,
                onDeleteNote = onDeleteNote
            )
        }
    }
}

/**
 * Industry-standard Material 3 Grouped Settings Card with leading icon badge, title, subtitle,
 * optional trailing header action, and structured content body.
 */
@Composable
private fun SettingsGroupCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    trailingHeaderAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0E1723),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (trailingHeaderAction != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingHeaderAction()
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            content()
        }
    }
}

/**
 * Industry-standard Material 3 Setting Switch Row with leading icon, primary title,
 * descriptive subtitle, and trailing Switch.
 */
@Composable
private fun IndustrySettingSwitchRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDockAgendaDialog(
    connectedAccounts: List<ConnectedAccountEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        subtitle: String,
        providerType: String,
        startOffsetMinutes: Int,
        durationMinutes: Int,
        locationOrSender: String
    ) -> Unit = { _, _, _, _, _, _ -> },
    onConfirmWithWindow: (
        title: String,
        subtitle: String,
        providerType: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        locationOrSender: String
    ) -> Unit = { _, _, _, _, _, _, _, _ -> }
) {
    val nowCal = remember { Calendar.getInstance() }
    val initStartTotal = remember { nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE) }
    val initEndTotal = remember { (initStartTotal + 60) % 1440 }

    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf("GMAIL") }
    var locationOrSender by remember { mutableStateOf("") }
    var startHour by remember { mutableIntStateOf(initStartTotal / 60) }
    var startMinute by remember { mutableIntStateOf(initStartTotal % 60) }
    var endHour by remember { mutableIntStateOf(initEndTotal / 60) }
    var endMinute by remember { mutableIntStateOf(initEndTotal % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Schedule Calendar / Activity Window",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Quick Activity Presets (Triggers StandBy Animation):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("Lunch Time", "Lunch Time // Meal Refuel", 12 to 30 to (13 to 30)),
                        Triple("Gym Time", "Gym Time // Workout Session", 18 to 0 to (19 to 0)),
                        Triple("Sleep Time", "Sleep Time // Night Rest", 23 to 0 to (7 to 0)),
                        Triple("Meeting", "Team Sync Meeting", (initStartTotal / 60) to (initStartTotal % 60) to ((initEndTotal / 60) to (initEndTotal % 60)))
                    ).forEach { (presetChip, presetTitle, windowPair) ->
                        FilterChip(
                            selected = title == presetTitle,
                            onClick = {
                                title = presetTitle
                                subtitle = "Calendar Schedule • $presetChip"
                                locationOrSender = when (presetChip) {
                                    "Lunch Time" -> "Dining / Cafe"
                                    "Gym Time" -> "Fitness Center"
                                    "Sleep Time" -> "Bedside Standby"
                                    else -> "Conference Call"
                                }
                                startHour = windowPair.first.first
                                startMinute = windowPair.first.second
                                endHour = windowPair.second.first
                                endMinute = windowPair.second.second
                            },
                            label = { Text(presetChip, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("preset_schedule_${presetChip.lowercase().replace(" ", "_")}")
                        )
                    }
                }

                // Connected Account / Source Selector
                Text(
                    text = "Connected Account / Calendar Source:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (connectedAccounts.isNotEmpty()) {
                        connectedAccounts.forEach { acc ->
                            val isSelected = subtitle == acc.emailAddress && providerType == acc.providerType
                            val chipColor = if (acc.providerType == "OUTLOOK") OutlookBlue else GmailCoral
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    providerType = acc.providerType
                                    subtitle = acc.emailAddress
                                },
                                label = {
                                    Text(
                                        "${acc.providerType}: ${acc.emailAddress}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipColor.copy(alpha = 0.22f),
                                    selectedLabelColor = chipColor
                                )
                            )
                        }
                    }
                    listOf("GMAIL" to "Gmail", "OUTLOOK" to "Outlook", "LOCAL" to "Dock Schedule").forEach { (key, label) ->
                        FilterChip(
                            selected = providerType == key && connectedAccounts.none { it.emailAddress == subtitle },
                            onClick = { providerType = key },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title (e.g., Lunch Time, Gym Time, Sleep Time, Meeting)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_agenda_title")
                )

                // TIME WINDOW RANGE SELECTOR (Start Time – End Time)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF101924), RoundedCornerShape(14.dp))
                        .border(
                            width = 1.dp,
                            color = RadianiteCyan.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp)
                        .testTag("schedule_time_window_range_selector"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TIME WINDOW RANGE",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = RadianiteCyan
                        )
                        Text(
                            text = "%02d:%02d – %02d:%02d".format(startHour, startMinute, endHour, endMinute),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = KilljoyGold
                        )
                    }

                    NightTimeEndpointAdjusterRow(
                        label = "Start Time",
                        hour = startHour,
                        minute = startMinute,
                        onAdjustMinutes = { delta ->
                            val total = ((startHour * 60 + startMinute + delta) % 1440 + 1440) % 1440
                            startHour = total / 60
                            startMinute = total % 60
                        },
                        testTagPrefix = "agenda_window_start"
                    )

                    NightTimeEndpointAdjusterRow(
                        label = "End Time",
                        hour = endHour,
                        minute = endMinute,
                        onAdjustMinutes = { delta ->
                            val total = ((endHour * 60 + endMinute + delta) % 1440 + 1440) % 1440
                            endHour = total / 60
                            endMinute = total % 60
                        },
                        testTagPrefix = "agenda_window_end"
                    )

                    // Quick Time Window Presets
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("Now + 45m", (initStartTotal / 60) to (initStartTotal % 60), ((initStartTotal + 45) % 1440 / 60) to ((initStartTotal + 45) % 60)),
                            Triple("12:30–13:30", 12 to 30, 13 to 30),
                            Triple("18:00–19:00", 18 to 0, 19 to 0),
                            Triple("23:00–07:00", 23 to 0, 7 to 0)
                        ).forEach { (lbl, sPair, ePair) ->
                            FilterChip(
                                selected = startHour == sPair.first && startMinute == sPair.second &&
                                    endHour == ePair.first && endMinute == ePair.second,
                                onClick = {
                                    startHour = sPair.first
                                    startMinute = sPair.second
                                    endHour = ePair.first
                                    endMinute = ePair.second
                                },
                                label = { Text(lbl, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Account / Summary Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = locationOrSender,
                    onValueChange = { locationOrSender = it },
                    label = { Text("Room / Link / Sender") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirmWithWindow(
                            title,
                            subtitle.ifBlank { "$providerType Synced Schedule" },
                            providerType,
                            startHour,
                            startMinute,
                            endHour,
                            endMinute,
                            locationOrSender
                        )
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_agenda_button")
            ) {
                Text("Pin Time Window")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDockNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, category: String, isPinned: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Quick Memo") }
    var isPinned by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Offline Dock Note",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Quick Memo", "Action Item", "AI Draft").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_note_title")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note Details / Markdown") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_note_content")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pin to StandBy Clock Screen",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() || content.isNotBlank()) {
                        onConfirm(
                            title.ifBlank { "Dock Memo" },
                            content,
                            category,
                            isPinned
                        )
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank() || content.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_note_button")
            ) {
                Text("Save Note")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun NightTimeEndpointAdjusterRow(
    label: String,
    hour: Int,
    minute: Int,
    onAdjustMinutes: (Int) -> Unit,
    testTagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "%02d:%02d".format(hour, minute),
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                "-1h" to -60,
                "-15m" to -15,
                "+15m" to 15,
                "+1h" to 60
            ).forEach { (btnLabel, deltaMins) ->
                OutlinedButton(
                    onClick = { onAdjustMinutes(deltaMins) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("${testTagPrefix}_${btnLabel.replace("+", "plus_").replace("-", "minus_")}")
                ) {
                    Text(
                        text = btnLabel,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
