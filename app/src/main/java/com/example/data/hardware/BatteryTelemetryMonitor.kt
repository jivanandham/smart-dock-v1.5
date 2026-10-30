package com.example.data.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

data class BatteryTelemetry(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false,
    val isDockedState: Boolean = false,
    val powerSource: String = "Discharging",
    val voltageMv: Int = 4100,
    val temperatureCelsius: Float = 28.5f,
    val currentMa: Int = 0
)

class BatteryTelemetryMonitor(private val context: Context) {

    fun observeTelemetry(): Flow<BatteryTelemetry> = callbackFlow {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        fun parseBatteryIntent(intent: Intent?): BatteryTelemetry {
            if (intent == null) return BatteryTelemetry()
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 100

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val powerSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast Dock"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB Hub Dock"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Qi Wireless Stand"
                BatteryManager.BATTERY_PLUGGED_DOCK -> "Pixel Stand Dock"
                else -> if (isCharging) "Power Connected" else "On Battery"
            }

            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4050)
            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 290)
            val tempC = tempTenths / 10f

            val rawCurrentUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            val currentMa = abs(rawCurrentUa / 1000)

            return BatteryTelemetry(
                levelPercent = percent.coerceIn(0, 100),
                isCharging = isCharging,
                isDockedState = isCharging || plugged != 0,
                powerSource = powerSource,
                voltageMv = if (voltage > 0) voltage else 4050,
                temperatureCelsius = if (tempC in -20f..80f) tempC else 29.0f,
                currentMa = currentMa
            )
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(parseBatteryIntent(intent))
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = context.registerReceiver(receiver, filter)
        trySend(parseBatteryIntent(initialIntent))

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }
}
