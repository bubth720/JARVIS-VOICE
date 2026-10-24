package com.jarvis.voice.voicestudio

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import java.util.Locale

/** Measures available counters; a missing/quantized battery counter is never called an energy proof. */
class OperationMetrics(private val context: Context) : AutoCloseable {
    private val began = SystemClock.elapsedRealtime()
    private val cpuBegan = Process.getElapsedCpuTime()
    private val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val charge = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    private val energy = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
    private fun temperature(): Double? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.div(10.0)
    private val temperatureBefore = temperature()
    private val thermalBefore = DeviceMetrics.thermal(context)
    private var monitor: MemorySampler? = null
    fun start(onSevere: () -> Unit) { monitor = MemorySampler(context, onSevere) }
    fun summary(): String {
        val after = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val energyAfter = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        fun delta(before: Long, end: Long, missing: Long): String = if (before == missing || end == missing) "indisponible" else (before - end).toString()
        val memory = monitor?.peakPssMiB?.takeIf { it>0 }?.let { String.format(Locale.FRENCH,"%.1f Mio",it) } ?: "pas d'échantillon"
        return String.format(Locale.FRENCH, "Durée : %d ms ; CPU processus : %d ms\nPSS maximal échantillonné (250 ms) : %s\nThermique initial/max/final : %d/%d/%d\nTempérature batterie initiale/finale : %s/%s °C\nVariation charge : %s µAh ; énergie : %s nWh\nCompteurs batterie parfois indisponibles ou trop grossiers ; pas une mesure certifiée de consommation.",
            SystemClock.elapsedRealtime()-began, Process.getElapsedCpuTime()-cpuBegan, memory, thermalBefore, monitor?.peakThermal ?: -1, DeviceMetrics.thermal(context), temperatureBefore ?: "?", temperature() ?: "?",
            delta(charge.toLong(), after.toLong(), Int.MIN_VALUE.toLong()), delta(energy, energyAfter, Long.MIN_VALUE))
    }
    fun json()=org.json.JSONObject().apply {
        put("elapsedMs",SystemClock.elapsedRealtime()-began);put("cpuProcessMs",Process.getElapsedCpuTime()-cpuBegan)
        put("pssPeakMiB",monitor?.peakPssMiB?.takeIf { it>0 } ?: org.json.JSONObject.NULL)
        put("pssSamplePeriodMs",250);put("thermalInitial",thermalBefore.takeIf { it>=0 } ?: org.json.JSONObject.NULL)
        put("thermalPeak",monitor?.peakThermal?.takeIf { it>=0 } ?: org.json.JSONObject.NULL)
        put("thermalFinal",DeviceMetrics.thermal(context).takeIf { it>=0 } ?: org.json.JSONObject.NULL)
        put("batteryTemperatureInitialC",temperatureBefore ?: org.json.JSONObject.NULL);put("batteryTemperatureFinalC",temperature() ?: org.json.JSONObject.NULL)
        val q=battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val e=battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        put("chargeDeltaMicroAh",if(charge==Int.MIN_VALUE || q==Int.MIN_VALUE) org.json.JSONObject.NULL else charge.toLong()-q.toLong())
        put("energyDeltaNWh",if(energy==Long.MIN_VALUE || e==Long.MIN_VALUE) org.json.JSONObject.NULL else energy-e)
        put("energyCertified",false)
    }
    override fun close() { monitor?.close() }
}
