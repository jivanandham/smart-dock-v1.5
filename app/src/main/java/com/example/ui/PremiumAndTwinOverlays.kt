package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.pettwin.PetTwin
import com.example.pettwin.PetTwinAnalyzer
import com.example.premium.PremiumFeature
import com.example.premium.PremiumManager
import com.example.ui.components.AiToyActivity
import com.example.ui.components.drawOrangeCatCompanion
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Paywall, pet twin editor and kids mode, drawn above the app. */
@Composable
fun PremiumAndTwinOverlays(viewModel: DockViewModel) {
    val paywall by viewModel.paywallOpen.collectAsStateWithLifecycle()
    val twinOpen by viewModel.petTwinOpen.collectAsStateWithLifecycle()
    val kidsOpen by viewModel.kidsSheetOpen.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val now by viewModel.currentTimeMillis.collectAsStateWithLifecycle()
    val message by PremiumManager.message.collectAsStateWithLifecycle()

    // Kids OK-to-wake full screen takes over during its window.
    val kidsPhase = settings.kidsPhase(now)
    if (kidsPhase != null) {
        KidsOkToWakeScreen(phase = kidsPhase, buddyName = settings.catName, wakeText = "%02d:%02d".format(settings.kidsWakeHour, settings.kidsWakeMinute))
    }

    if (paywall) PaywallDialog(onDismiss = { viewModel.closePaywall() })
    if (twinOpen) PetTwinDialog(viewModel)
    if (kidsOpen) {
        KidsSettingsDialog(
            enabled = settings.kidsModeEnabled,
            bedH = settings.kidsBedHour, bedM = settings.kidsBedMinute,
            wakeH = settings.kidsWakeHour, wakeM = settings.kidsWakeMinute,
            onSave = { e, bh, bm, wh, wm -> viewModel.saveKidsMode(e, bh, bm, wh, wm) },
            onDismiss = { viewModel.closeKidsSheet() }
        )
    }
    val msg = message
    if (msg != null) {
        LaunchedEffect(msg) {
            delay(3500)
            PremiumManager.clearMessage()
        }
        AlertDialog(
            onDismissRequest = { PremiumManager.clearMessage() },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { PremiumManager.clearMessage() }) { Text("OK") } }
        )
    }
}

// -------------------------------------------------------------------------------------------
// Paywall
// -------------------------------------------------------------------------------------------

@Composable
fun PaywallDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prices by PremiumManager.prices.collectAsStateWithLifecycle()
    val premium by PremiumManager.isPremium.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (premium) "Premium is unlocked" else "Unlock Premium") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumFeature.entries.forEach { f -> Text("✓  ${f.label}", fontSize = 14.sp) }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Always free: the clock, reliable alarms, one companion, calendar, weather and basic wellness reminders.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!premium) {
                    val activity = context.findActivity()
                    Button(onClick = { activity?.let { PremiumManager.launchPurchase(it, PremiumManager.Plan.YEARLY) } }, modifier = Modifier.fillMaxWidth()) {
                        Text("Yearly · ${prices.yearly ?: "€19.99"}  (best value)")
                    }
                    OutlinedButton(onClick = { activity?.let { PremiumManager.launchPurchase(it, PremiumManager.Plan.MONTHLY) } }, modifier = Modifier.fillMaxWidth()) {
                        Text("Monthly · ${prices.monthly ?: "€2.99"}")
                    }
                    OutlinedButton(onClick = { activity?.let { PremiumManager.launchPurchase(it, PremiumManager.Plan.LIFETIME) } }, modifier = Modifier.fillMaxWidth()) {
                        Text("Lifetime · ${prices.lifetime ?: "€29.99"}  (pay once)")
                    }
                    Text("Cancel any time in Google Play. Prices shown by Google Play in your currency.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { PremiumManager.restore() }) { Text("Restore purchases") }
                if (BuildConfig.DEBUG) {
                    TextButton(onClick = { PremiumManager.setDebugUnlock(!premium) }) {
                        Text(if (premium) "Debug: lock Premium" else "Debug: unlock Premium (testing only)")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// -------------------------------------------------------------------------------------------
// Pet twin editor
// -------------------------------------------------------------------------------------------

private val EyeChoices = listOf(
    "Amber" to Color(0xFFD5A22A), "Green" to Color(0xFF9DB84A), "Blue" to Color(0xFF6FB3DD),
    "Copper" to Color(0xFFC8742A), "Brown" to Color(0xFF7A4E2A)
)
private val FurChoices = listOf(
    Color(0xFFE08A3C), Color(0xFFF2D2A9), Color(0xFF8E939B), Color(0xFF2A2724),
    Color(0xFFF7F3EC), Color(0xFF8A5A33), Color(0xFFC9A675), Color(0xFF5F646C)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetTwinDialog(viewModel: DockViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing by viewModel.petTwin.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var twin by remember { mutableStateOf(existing ?: PetTwin(enabled = true, name = settings.catName)) }
    var consented by remember { mutableStateOf(existing != null) }
    var analyzing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            analyzing = true
            scope.launch {
                val result = PetTwinAnalyzer(context).analyze(uri, twin.name)
                twin = result.twin.copy(name = twin.name.ifBlank { result.twin.name })
                note = result.note ?: if (result.usedAi) "Here's your twin! Adjust anything that's not quite right." else null
                analyzing = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { viewModel.closePetTwin() },
        title = { Text("Pet twin") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TwinPreview(twin)
                if (!consented) {
                    Text(
                        "Pick a photo of your pet and we'll build a matching companion. The photo is sent once to " +
                            "Google's Gemini AI for analysis (if enabled) and is never stored. Only the colours and markings are kept. " +
                            "Prefer not to upload? Use the editor below instead.",
                        fontSize = 12.sp
                    )
                    Button(onClick = { consented = true; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("I agree · choose a photo")
                    }
                } else {
                    OutlinedButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !analyzing
                    ) { Text(if (analyzing) "Analysing…" else "Choose a photo") }
                }
                if (analyzing) CircularProgressIndicator(modifier = Modifier.size(28.dp))
                note?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }

                OutlinedTextField(twin.name, { twin = twin.copy(name = it.take(24)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text("Main fur", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                ColorRow(FurChoices, Color(twin.baseColor)) { twin = twin.copy(baseColor = it.toArgb()) }
                Text("Patch colour (calico / tortie)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                ColorRow(listOf(Color.Transparent) + FurChoices, twin.secondaryColor?.let { Color(it) } ?: Color.Transparent) {
                    twin = if (it == Color.Transparent) twin.copy(secondaryColor = null, patches = false) else twin.copy(secondaryColor = it.toArgb(), patches = true)
                }
                Text("Eyes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EyeChoices.forEach { (label, c) ->
                        val selected = twin.eyeColor == c.toArgb()
                        Text(
                            label, fontSize = 12.sp,
                            modifier = Modifier.clip(CircleShape)
                                .background(if (selected) c.copy(alpha = 0.3f) else Color.Transparent)
                                .border(1.dp, c, CircleShape)
                                .clickable { twin = twin.copy(eyeColor = c.toArgb()) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Text("Stripes", fontSize = 12.sp)
                Slider(value = twin.stripes, onValueChange = { twin = twin.copy(stripes = it) })
                ToggleLine("White chest", twin.whiteChest) { twin = twin.copy(whiteChest = it) }
                ToggleLine("White socks", twin.whiteSocks) { twin = twin.copy(whiteSocks = it) }
                ToggleLine("Tuxedo face", twin.tuxedo) { twin = twin.copy(tuxedo = it) }
                ToggleLine("Darker ears, face & tail (colour-point)", twin.darkPoints) { twin = twin.copy(darkPoints = it) }
                if (existing != null) {
                    ToggleLine("Use my pet twin", twin.enabled) { twin = twin.copy(enabled = it) }
                }
            }
        },
        confirmButton = { Button(onClick = { viewModel.savePetTwin(twin) }, enabled = !analyzing) { Text("Save twin") } },
        dismissButton = { TextButton(onClick = { viewModel.closePetTwin() }) { Text("Cancel") } }
    )
}

@Composable
private fun TwinPreview(twin: PetTwin) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (last != 0L) t += (n - last) / 1_000_000_000f
                last = n
            }
        }
    }
    val palette = twin.palette
    val markings = twin.markings
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        val u = minOf(size.height / 78f, size.width / 140f)
        val blink = if ((t % 4f) > 3.85f) 1f else 0f
        drawOrangeCatCompanion(
            activity = AiToyActivity.TIMER, roamX = size.width / 2f, stageCenterX = size.width / 2f,
            floorY = size.height - 6f * u, u = u, facing = 1f, walkDistancePx = 0f, walkBlend = 0f,
            timeSec = t, blink = blink, gazeX = 0.3f, gazeY = 0.2f, hopOffsetPx = 0f,
            squashX = 1f, squashY = 1f, tiltDeg = 0f, accent = Color(0xFFF6B86E), sandProgress = (t / 10f) % 1f,
            palette = palette, markings = markings
        )
    }
}

@Composable
private fun ColorRow(choices: List<Color>, selected: Color, onPick: (Color) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { c ->
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (c == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else c)
                    .border(if (c.toArgb() == selected.toArgb()) 3.dp else 1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center
            ) {
                if (c == Color.Transparent) Text("—", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Captures the current screen and opens the share sheet ("look at my buddy!"). */
fun shareCompanionScreenshot(context: Context, buddyName: String) {
    val activity = context.findActivity() ?: return
    try {
        val bitmap = activity.window.decorView.rootView.drawToBitmap()
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "my-buddy.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Meet $buddyName, my bedside buddy 🐾")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share your buddy").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Couldn't capture the screen", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// -------------------------------------------------------------------------------------------
// Kids OK-to-wake
// -------------------------------------------------------------------------------------------

@Composable
private fun KidsOkToWakeScreen(phase: String, buddyName: String, wakeText: String) {
    var t by remember { mutableFloatStateOf(0f) }
    var exitHold by remember { mutableIntStateOf(0) }
    var hidden by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { n -> if (last != 0L) t += (n - last) / 1_000_000_000f; last = n }
        }
    }
    if (hidden) return
    val awake = phase == "WAKE"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (awake) Color(0xFF0D2A16) else Color.Black)
            .clickable { exitHold++; if (exitHold >= 5) hidden = true }, // parents: tap 5 times to peek at the app
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                val u = minOf(size.height / 78f, size.width / 140f)
                if (awake) {
                    drawCircle(Color(0xFF3DDC84).copy(alpha = 0.25f), radius = 60f * u, center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.55f))
                }
                drawOrangeCatCompanion(
                    activity = if (awake) AiToyActivity.ROAMING else AiToyActivity.SLEEPING,
                    roamX = size.width / 2f, stageCenterX = size.width / 2f, floorY = size.height - 6f * u, u = u,
                    facing = 1f, walkDistancePx = 0f, walkBlend = 0f, timeSec = t, blink = 0f, gazeX = 0f, gazeY = 0f,
                    hopOffsetPx = if (awake) -kotlin.math.abs(kotlin.math.sin(t * 3f)) * 10f * u else 0f,
                    squashX = 1f, squashY = 1f, tiltDeg = 0f, accent = Color(0xFF3DDC84), sandProgress = 0f,
                    ambientLight = if (awake) Color(0xFFFFE08A).copy(alpha = 0.2f) else Color(0xFF3A4BC8).copy(alpha = 0.35f)
                )
            }
            Text(
                if (awake) "Good morning! You can get up ☀️" else "Stay in bed. $buddyName is still sleeping 🌙",
                color = if (awake) Color(0xFF9BF0B8) else Color(0xFF6B2A24),
                fontSize = if (awake) 30.sp else 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!awake) {
                Spacer(Modifier.height(6.dp))
                Text("Wake-up time: $wakeText", color = Color(0xFF4A1E1A), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun KidsSettingsDialog(
    enabled: Boolean, bedH: Int, bedM: Int, wakeH: Int, wakeM: Int,
    onSave: (Boolean, Int, Int, Int, Int) -> Unit, onDismiss: () -> Unit
) {
    var on by remember { mutableStateOf(enabled) }
    var bed by remember { mutableIntStateOf(bedH * 60 + bedM) }
    var wake by remember { mutableIntStateOf(wakeH * 60 + wakeM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kids OK-to-wake") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Turns this device into a children's sleep clock: the companion sleeps on a dark screen until " +
                        "wake-up time, then wakes up on a green screen to show it's OK to get up.",
                    fontSize = 13.sp
                )
                ToggleLine("Kids mode", on) { on = it }
                Text("Bedtime: %02d:%02d".format(bed / 60, bed % 60), fontSize = 13.sp)
                Slider(value = bed.toFloat(), onValueChange = { bed = (it / 15).toInt() * 15 }, valueRange = (17 * 60f)..(22 * 60f))
                Text("Wake-up: %02d:%02d".format(wake / 60, wake % 60), fontSize = 13.sp)
                Slider(value = wake.toFloat(), onValueChange = { wake = (it / 15).toInt() * 15 }, valueRange = (5 * 60f)..(9 * 60f))
                Text("Parents: tap the kids screen 5 times to use the app.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onSave(on, bed / 60, bed % 60, wake / 60, wake % 60) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
