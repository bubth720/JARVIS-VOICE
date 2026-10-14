package com.jarvis.voice.pocketprobe

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.system.Os
import android.system.OsConstants
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MemorySampler(private val context: Context, private val onSevere: () -> Unit = {}) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val peak = AtomicInteger(0)
    private val thermalPeak = AtomicInteger(-1)
    init { executor.scheduleWithFixedDelay({
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        peak.accumulateAndGet(info.totalPss, ::maxOf)
        val thermal = DeviceMetrics.thermal(context)
        thermalPeak.accumulateAndGet(thermal, ::maxOf)
        if (thermal >= 3) onSevere()
    }, 0, 250, TimeUnit.MILLISECONDS) }
    val peakThermal get() = thermalPeak.get()
    val peakPssMiB: Double get() = peak.get() / 1024.0
    override fun close() { executor.shutdownNow() }
}

object DeviceMetrics {
    fun summary(context: Context): String {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        val page = Os.sysconf(OsConstants._SC_PAGESIZE)
        return "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
            "\nABI : ${Build.SUPPORTED_ABIS.joinToString()}\nPages mémoire : $page octets" +
            "\nRAM totale : ${info.totalMem / 1048576} Mio ; disponible : ${info.availMem / 1048576} Mio" +
            "\nRuntime : ONNX 1.27.0, CPU uniquement\nPack : français 6 couches INT8 ; voix prédéfinie Alba" +
            "\nBibliothèques ARM64 vérifiées pour l'alignement 16 Ko à la compilation." +
            "\nLe fonctionnement sur cet appareil reste à valider par vos essais."
    }
    fun thermal(context: Context): Int = if (Build.VERSION.SDK_INT >= 29)
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else -1
    fun isThermallySevere(context: Context): Boolean = Build.VERSION.SDK_INT >= 29 &&
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
}
