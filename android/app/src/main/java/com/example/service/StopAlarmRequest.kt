package com.example.service

/**
 * Проверка параметров будильника до запуска foreground-сервиса.
 *
 * Сервис, запущенный через startForegroundService, обязан успеть вызвать
 * startForeground; остановка раньше этого роняет процесс. Поэтому заведомо
 * неверный запрос отсекается ещё в приложении и сервис не стартует вовсе.
 */
object StopAlarmRequest {
    val RADIUS_RANGE_METERS = 100.0..2_000.0

    fun isValid(lat: Double, lon: Double, radiusMeters: Double): Boolean =
        lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0 &&
            radiusMeters.isFinite() && radiusMeters in RADIUS_RANGE_METERS
}
