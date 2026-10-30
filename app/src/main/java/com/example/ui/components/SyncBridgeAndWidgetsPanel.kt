package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.calendar.AccountProviderBadge
import com.example.data.calendar.UnifiedScheduleItem
import com.example.data.local.ConnectedAccountEntity
import com.example.data.local.DockAlarmEntity
import com.example.ui.theme.ChargingEmerald
import com.example.ui.theme.GmailCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KilljoyGold
import com.example.ui.theme.OmenViolet
import com.example.ui.theme.OutlookBlue
import com.example.ui.theme.RadianiteCyan
import com.example.ui.theme.ValorantCrimson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncBridgeAndWidgetsPanel(
    calendarPermissionGranted: Boolean,
    syncedAccounts: List<Pair<String, AccountProviderBadge>>,
    connectedAccounts: List<ConnectedAccountEntity> = emptyList(),
    alarms: List<DockAlarmEntity> = emptyList(),
    chronoState: ActiveChronoHudState = ActiveChronoHudState(),
    scheduleItems: List<UnifiedScheduleItem>,
    selectedFilter: AccountProviderBadge?,
    onSelectFilter: (AccountProviderBadge?) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRefreshCalendars: () -> Unit,
    onOpenAccountSyncSettings: () -> Unit,
    onOpenAddAccountSettings: () -> Unit,
    onAddConnectedAccount: (email: String, displayName: String, providerType: String) -> Unit = { _, _, _ -> },
    onToggleConnectedAccountSync: (ConnectedAccountEntity) -> Unit = {},
    onRemoveConnectedAccount: (ConnectedAccountEntity) -> Unit = {},
    onAddAlarm: (hour: Int, minute: Int, label: String) -> Unit = { _, _, _ -> },
    onToggleAlarm: (DockAlarmEntity) -> Unit = {},
    onDeleteAlarm: (Long) -> Unit = {},
    onTriggerAlarmNow: (DockAlarmEntity?) -> Unit = {},
    onStartTimer: (durationSeconds: Int, label: String) -> Unit = { _, _ -> },
    onPauseResumeTimer: () -> Unit = {},
    onStartPandoraTimer: (durationMinutes: Int, phase: PandoraPhase) -> Unit = { _, _ -> },
    onPauseResumePandoraTimer: () -> Unit = {},
    onSnoozeChrono: (AiToyActivity) -> Unit = {},
    onStopChrono: (AiToyActivity) -> Unit = {},
    onInsertNativeCalendarEvent: () -> Unit,
    onOpenAddDockItemDialog: () -> Unit,
    onToggleItemCompleted: (UnifiedScheduleItem) -> Unit,
    onDeleteCustomItem: (UnifiedScheduleItem) -> Unit,
    onOpenGmailApp: () -> Unit,
    onOpenOutlookApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFmt = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
    var showAddAccountComposer by remember { mutableStateOf(false) }
    var newAccountEmail by remember { mutableStateOf("") }
    var newAccountLabel by remember { mutableStateOf("") }
    var newAccountProvider by remember { mutableStateOf("GMAIL") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sync_bridge_panel"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 0: Alarm, Countdown Timer & Pandora Focus Timer Control Hub
        AlarmsTimerPandoraControlCard(
            alarms = alarms,
            chronoState = chronoState,
            onAddAlarm = onAddAlarm,
            onToggleAlarm = onToggleAlarm,
            onDeleteAlarm = onDeleteAlarm,
            onTriggerAlarmNow = onTriggerAlarmNow,
            onStartTimer = onStartTimer,
            onPauseResumeTimer = onPauseResumeTimer,
            onStartPandoraTimer = onStartPandoraTimer,
            onPauseResumePandoraTimer = onPauseResumePandoraTimer,
            onSnoozeChrono = onSnoozeChrono,
            onStopChrono = onStopChrono
        )

        // Card 1: Multiple Gmail & Microsoft Outlook Connected Accounts (Add / Remove / Toggle)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("multi_accounts_manager_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Account Sync Bridge",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Connected Gmail & Outlook Accounts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Connect multiple Gmail & Microsoft Outlook accounts • Add or Remove anytime",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { showAddAccountComposer = !showAddAccountComposer },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_toggle_add_account_form")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Add Gmail or Outlook Account",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Account", style = MaterialTheme.typography.labelMedium)
                        }

                        IconButton(
                            onClick = onRefreshCalendars,
                            modifier = Modifier.testTag("refresh_calendars_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Calendars",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (showAddAccountComposer) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0A121C),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = RadianiteCyan.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .testTag("add_connected_account_form")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "CONNECT NEW GMAIL OR OUTLOOK ACCOUNT",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = RadianiteCyan
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = newAccountProvider == "GMAIL",
                                    onClick = { newAccountProvider = "GMAIL" },
                                    label = { Text("Google Gmail") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GmailCoral.copy(alpha = 0.22f),
                                        selectedLabelColor = GmailCoral
                                    ),
                                    modifier = Modifier.testTag("select_provider_gmail")
                                )
                                FilterChip(
                                    selected = newAccountProvider == "OUTLOOK",
                                    onClick = { newAccountProvider = "OUTLOOK" },
                                    label = { Text("Microsoft Outlook") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = OutlookBlue.copy(alpha = 0.22f),
                                        selectedLabelColor = OutlookBlue
                                    ),
                                    modifier = Modifier.testTag("select_provider_outlook")
                                )
                            }

                            OutlinedTextField(
                                value = newAccountEmail,
                                onValueChange = { newAccountEmail = it },
                                label = {
                                    Text(
                                        if (newAccountProvider == "OUTLOOK") {
                                            "Outlook Email (e.g. team@outlook.com)"
                                        } else {
                                            "Gmail Address (e.g. dev@gmail.com)"
                                        }
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_account_email")
                            )

                            OutlinedTextField(
                                value = newAccountLabel,
                                onValueChange = { newAccountLabel = it },
                                label = { Text("Account Label (e.g. Work Gmail, Client Outlook)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_account_label")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { showAddAccountComposer = false }
                                ) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (newAccountEmail.isNotBlank()) {
                                            onAddConnectedAccount(
                                                newAccountEmail.trim(),
                                                newAccountLabel.trim().ifBlank {
                                                    if (newAccountProvider == "OUTLOOK") "Outlook Calendar" else "Gmail Calendar"
                                                },
                                                newAccountProvider
                                            )
                                            newAccountEmail = ""
                                            newAccountLabel = ""
                                            showAddAccountComposer = false
                                        }
                                    },
                                    enabled = newAccountEmail.isNotBlank(),
                                    modifier = Modifier.testTag("confirm_add_account_button")
                                ) {
                                    Text("Connect Account")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Connected Gmail & Outlook Accounts List with Toggle & Remove (Delete)
                Text(
                    text = "CONNECTED GMAIL & OUTLOOK ACCOUNTS (${connectedAccounts.size})",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (connectedAccounts.isEmpty()) {
                    Text(
                        text = "No Gmail or Outlook accounts connected yet. Tap 'Add Account' above to connect multiple Gmail and Microsoft Outlook accounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        connectedAccounts.forEach { acc ->
                            val isOutlook = acc.providerType.equals("OUTLOOK", ignoreCase = true)
                            val badgeColor = if (isOutlook) OutlookBlue else GmailCoral
                            val providerBadgeText = if (isOutlook) "OUTLOOK" else "GMAIL"

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = badgeColor.copy(alpha = if (acc.isSyncEnabled) 0.55f else 0.2f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .testTag("connected_account_row_${acc.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = badgeColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = badgeColor.copy(alpha = 0.18f)
                                                ) {
                                                    Text(
                                                        text = providerBadgeText,
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = badgeColor,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = acc.displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = acc.emailAddress,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Switch(
                                            checked = acc.isSyncEnabled,
                                            onCheckedChange = { onToggleConnectedAccountSync(acc) },
                                            modifier = Modifier.testTag("toggle_account_sync_${acc.id}")
                                        )
                                        IconButton(
                                            onClick = { onRemoveConnectedAccount(acc) },
                                            modifier = Modifier.testTag("remove_connected_account_${acc.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Remove ${acc.emailAddress}",
                                                tint = ValorantCrimson
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!calendarPermissionGranted) {
                    OutlinedButton(
                        onClick = onRequestCalendarPermission,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_grant_calendar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Also Sync Device CalendarContract", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // System Intent Bridge Actions
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenAccountSyncSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_open_accounts")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("System Accounts", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onOpenAddAccountSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_add_account")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OS Account Setup", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onOpenGmailApp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_open_gmail")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = GmailCoral,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Gmail", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onOpenOutlookApp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_open_outlook")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = OutlookBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Outlook", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Card 2: Smart Widget Layer (Unified Agenda & Email Summary Cards)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smart Widget Layer: Unified Agenda",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Combined Gmail, Outlook & Dock Schedule Feed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onInsertNativeCalendarEvent,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_insert_system_event")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "System Calendar Event",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Button(
                            onClick = onOpenAddDockItemDialog,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("btn_add_agenda_item")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pin Item", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips by Provider
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderFilterChip(
                        label = "All Sources",
                        selected = selectedFilter == null,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = { onSelectFilter(null) },
                        tag = "filter_all"
                    )
                    ProviderFilterChip(
                        label = "Gmail / Google",
                        selected = selectedFilter == AccountProviderBadge.GMAIL,
                        accentColor = GmailCoral,
                        onClick = { onSelectFilter(AccountProviderBadge.GMAIL) },
                        tag = "filter_gmail"
                    )
                    ProviderFilterChip(
                        label = "Outlook",
                        selected = selectedFilter == AccountProviderBadge.OUTLOOK,
                        accentColor = OutlookBlue,
                        onClick = { onSelectFilter(AccountProviderBadge.OUTLOOK) },
                        tag = "filter_outlook"
                    )
                    ProviderFilterChip(
                        label = "Dock Local",
                        selected = selectedFilter == AccountProviderBadge.SYSTEM,
                        accentColor = ChargingEmerald,
                        onClick = { onSelectFilter(AccountProviderBadge.SYSTEM) },
                        tag = "filter_local"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (scheduleItems.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No upcoming agenda or email digest cards.",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sync your Gmail & Microsoft Outlook accounts above or tap '+ Pin Item' to add a meeting or email follow-up card to your StandBy dock.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        scheduleItems.forEach { item ->
                            ScheduleItemCard(
                                item = item,
                                timeFmt = timeFmt,
                                onToggleCompleted = { onToggleItemCompleted(item) },
                                onDelete = { onDeleteCustomItem(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderFilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accentColor.copy(alpha = 0.22f),
            selectedLabelColor = accentColor
        ),
        modifier = Modifier.testTag(tag)
    )
}

@Composable
fun ScheduleItemCard(
    item: UnifiedScheduleItem,
    timeFmt: SimpleDateFormat,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit
) {
    val badgeColor = providerColor(item.providerBadge)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = badgeColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left color accent bar
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Spacer(modifier = Modifier.width(12.dp))

            if (item.isCustomLocalItem) {
                IconButton(
                    onClick = onToggleCompleted,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (item.isCompleted) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.RadioButtonUnchecked
                        },
                        contentDescription = "Toggle completed",
                        tint = if (item.isCompleted) ChargingEmerald else badgeColor
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = item.providerBadge.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = if (item.isAllDay) {
                            "ALL DAY"
                        } else {
                            "${timeFmt.format(Date(item.startTimeMillis))} – ${
                                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.endTimeMillis))
                            }"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (item.accountName.isNotBlank() || item.location.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.location.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${item.location} • ${item.accountName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = item.accountName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (item.isCustomLocalItem) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete item",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun providerColor(badge: AccountProviderBadge): Color = when (badge) {
    AccountProviderBadge.GMAIL -> GmailCoral
    AccountProviderBadge.OUTLOOK -> OutlookBlue
    AccountProviderBadge.SYSTEM -> ChargingEmerald
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmsTimerPandoraControlCard(
    alarms: List<DockAlarmEntity>,
    chronoState: ActiveChronoHudState,
    onAddAlarm: (hour: Int, minute: Int, label: String) -> Unit,
    onToggleAlarm: (DockAlarmEntity) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onTriggerAlarmNow: (DockAlarmEntity?) -> Unit,
    onStartTimer: (durationSeconds: Int, label: String) -> Unit,
    onPauseResumeTimer: () -> Unit,
    onStartPandoraTimer: (durationMinutes: Int, phase: PandoraPhase) -> Unit,
    onPauseResumePandoraTimer: () -> Unit,
    onSnoozeChrono: (AiToyActivity) -> Unit,
    onStopChrono: (AiToyActivity) -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Alarm, 1 = Timer, 2 = Pandora Timer
    var newAlarmHour by remember { mutableIntStateOf(7) }
    var newAlarmMinute by remember { mutableIntStateOf(30) }
    var newAlarmLabel by remember { mutableStateOf("Morning Wake-Up") }
    var showAddAlarmRow by remember { mutableStateOf(false) }

    val activeChronoActivity = when {
        chronoState.isAlarmRinging -> AiToyActivity.ALARM
        chronoState.isTimerRinging || chronoState.isTimerRunning -> AiToyActivity.TIMER
        chronoState.isPandoraRinging || chronoState.isPandoraRunning -> AiToyActivity.PANDORA_TIMER
        selectedSubTab == 0 -> AiToyActivity.ALARM
        selectedSubTab == 1 -> AiToyActivity.TIMER
        else -> AiToyActivity.PANDORA_TIMER
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarms_timer_pandora_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(activeChronoActivity.accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (selectedSubTab) {
                                0 -> Icons.Default.Alarm
                                else -> Icons.Default.Timer
                            },
                            contentDescription = "Alarms, Timer & Focus Timer",
                            tint = activeChronoActivity.accentColor
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Alarms, Timer & Focus",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Live Robot Animation + Snooze (+5m) & Stop Controls",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick Snooze & Stop action buttons always accessible
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onSnoozeChrono(activeChronoActivity) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("card_btn_snooze_chrono")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = "Snooze",
                            tint = KilljoyGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Snooze", style = MaterialTheme.typography.labelSmall, color = KilljoyGold)
                    }
                    Button(
                        onClick = { onStopChrono(activeChronoActivity) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ValorantCrimson),
                        modifier = Modifier.testTag("card_btn_stop_chrono")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Mode Selector Chips: Alarm | Countdown Timer | Pandora Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    label = { Text("Alarms (${alarms.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ValorantCrimson.copy(alpha = 0.22f),
                        selectedLabelColor = ValorantCrimson
                    ),
                    modifier = Modifier.testTag("chrono_tab_alarms")
                )
                FilterChip(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    label = {
                        Text(
                            if (chronoState.isTimerRunning || chronoState.isTimerRinging) {
                                "Timer (${formatSecondsMmSs(chronoState.timerRemainingSeconds)})"
                            } else {
                                "Timer"
                            }
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RadianiteCyan.copy(alpha = 0.22f),
                        selectedLabelColor = RadianiteCyan
                    ),
                    modifier = Modifier.testTag("chrono_tab_timer")
                )
                FilterChip(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    label = {
                        Text(
                            if (chronoState.isPandoraRunning || chronoState.isPandoraRinging) {
                                "Focus (${formatSecondsMmSs(chronoState.pandoraRemainingSeconds)})"
                            } else {
                                "Focus Timer"
                            }
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OmenViolet.copy(alpha = 0.22f),
                        selectedLabelColor = OmenViolet
                    ),
                    modifier = Modifier.testTag("chrono_tab_pandora")
                )
            }

            when (selectedSubTab) {
                0 -> {
                    // ALARMS SECTION
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (chronoState.isAlarmRinging) {
                                "RINGING NOW: ${chronoState.ringingAlarmLabel} (${chronoState.ringingAlarmTimeText})"
                            } else {
                                "Scheduled Dock Alarms"
                            },
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (chronoState.isAlarmRinging) ValorantCrimson else MaterialTheme.colorScheme.secondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onTriggerAlarmNow(alarms.firstOrNull()) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("btn_ring_alarm_preview")
                            ) {
                                Text("Ring Animation", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { showAddAlarmRow = !showAddAlarmRow },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("btn_toggle_add_alarm")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Alarm", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (showAddAlarmRow) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0A121C),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ValorantCrimson.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .testTag("add_alarm_composer")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "New Alarm Time: %02d:%02d".format(newAlarmHour, newAlarmMinute),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = KilljoyGold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("-1h" to -60, "-15m" to -15, "+15m" to 15, "+1h" to 60).forEach { (lbl, delta) ->
                                            OutlinedButton(
                                                onClick = {
                                                    val total = ((newAlarmHour * 60 + newAlarmMinute + delta) % 1440 + 1440) % 1440
                                                    newAlarmHour = total / 60
                                                    newAlarmMinute = total % 60
                                                }
                                            ) {
                                                Text(lbl, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = newAlarmLabel,
                                    onValueChange = { newAlarmLabel = it },
                                    label = { Text("Alarm Label") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_alarm_label")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            onAddAlarm(
                                                newAlarmHour,
                                                newAlarmMinute,
                                                newAlarmLabel.ifBlank { "Dock Alarm" }
                                            )
                                            showAddAlarmRow = false
                                        },
                                        modifier = Modifier.testTag("confirm_save_alarm_button")
                                    ) {
                                        Text("Save Alarm")
                                    }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        alarms.forEach { alarm ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = if (alarm.isEnabled) ValorantCrimson.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .testTag("alarm_row_${alarm.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "%02d:%02d".format(alarm.hour, alarm.minute),
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = alarm.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onTriggerAlarmNow(alarm) },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("trigger_alarm_${alarm.id}")
                                        ) {
                                            Text("Ring", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Switch(
                                            checked = alarm.isEnabled,
                                            onCheckedChange = { onToggleAlarm(alarm) },
                                            modifier = Modifier.testTag("toggle_alarm_${alarm.id}")
                                        )
                                        IconButton(
                                            onClick = { onDeleteAlarm(alarm.id) },
                                            modifier = Modifier.testTag("delete_alarm_${alarm.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Alarm",
                                                tint = ValorantCrimson
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // COUNTDOWN TIMER SECTION
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "COUNTDOWN TIMER: ${formatSecondsMmSs(chronoState.timerRemainingSeconds)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RadianiteCyan
                            )
                            Text(
                                text = if (chronoState.isTimerRinging) {
                                    "TIMER COMPLETE • Tap Snooze (+5m) or Stop"
                                } else if (chronoState.isTimerRunning) {
                                    "Running (${chronoState.timerLabel}) • Robot Timer Animation Active"
                                } else {
                                    "Select a preset to launch Countdown Timer + Robot Animation"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = onPauseResumeTimer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_pause_resume_timer")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (chronoState.isTimerRunning) "Pause" else "Start / Resume")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            60 to "1 Min",
                            180 to "3 Min",
                            300 to "5 Min",
                            600 to "10 Min",
                            900 to "15 Min",
                            1800 to "30 Min"
                        ).forEach { (secs, label) ->
                            FilterChip(
                                selected = chronoState.timerTotalSeconds == secs && chronoState.isTimerRunning,
                                onClick = { onStartTimer(secs, "$label Timer") },
                                label = { Text(label) },
                                modifier = Modifier.testTag("timer_preset_${secs}s")
                            )
                        }
                    }
                }

                2 -> {
                    // PANDORA FOCUS TIMER SECTION
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "FOCUS ${chronoState.pandoraPhase.label}: ${formatSecondsMmSs(chronoState.pandoraRemainingSeconds)} (Cycle #${chronoState.pandoraCompletedCycles + 1})",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OmenViolet
                            )
                            Text(
                                text = if (chronoState.isPandoraRinging) {
                                    "FOCUS SESSION COMPLETE • Tap Snooze (+5m) or Stop"
                                } else if (chronoState.isPandoraRunning) {
                                    "Focus Orb Animation Active on Dock"
                                } else {
                                    "Launch a Focus or Recharge Break session"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = onPauseResumePandoraTimer,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OmenViolet),
                            modifier = Modifier.testTag("btn_pause_resume_pandora")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (chronoState.isPandoraRunning) "Pause" else "Start Focus")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple(25, PandoraPhase.FOCUS, "25m Focus"),
                            Triple(50, PandoraPhase.FOCUS, "50m Deep Focus"),
                            Triple(5, PandoraPhase.SHORT_BREAK, "5m Short Break"),
                            Triple(15, PandoraPhase.LONG_BREAK, "15m Recharge Break")
                        ).forEach { (mins, phase, label) ->
                            FilterChip(
                                selected = chronoState.pandoraTotalSeconds == mins * 60 &&
                                    chronoState.pandoraPhase == phase &&
                                    chronoState.isPandoraRunning,
                                onClick = { onStartPandoraTimer(mins, phase) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("pandora_preset_${mins}m")
                            )
                        }
                    }
                }
            }
        }
    }
}

