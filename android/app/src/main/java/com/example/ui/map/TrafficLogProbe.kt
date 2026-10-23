package com.example.ui.map

import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Читает предупреждения MapKit из logcat собственного процесса.
 *
 * Приложению доступны только свои записи журнала (Android 4.1+), поэтому
 * разрешения не нужны. Нужен ровно один факт: ответил ли сервер Яндекса 403 на
 * запрос векторного слоя пробок — через API MapKit этого не узнать.
 */
object TrafficLogProbe {

    suspend fun readMapKitWarnings(): List<String> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(
                "logcat", "-d", "-v", "brief", "-t", "2000",
                "--pid=${Process.myPid()}",
                "yandex.maps:W", "*:S"
            ).redirectErrorStream(true).start()
            val lines = process.inputStream.bufferedReader().use { it.readLines() }
            process.waitFor(3, TimeUnit.SECONDS)
            process.destroy()
            lines
        } catch (_: Throwable) {
            // Журнал недоступен (редкие прошивки) — просто нет диагностики.
            emptyList()
        }
    }

    suspend fun isTrafficLayerForbidden(): Boolean =
        TrafficPolicy.isTrafficLayerForbidden(readMapKitWarnings())
}
