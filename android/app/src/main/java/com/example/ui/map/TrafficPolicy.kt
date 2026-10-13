package com.example.ui.map

import com.yandex.mapkit.map.MapType

/**
 * Слой пробок Яндекса и всё, что пассажир видит о его состоянии.
 *
 * Логика вынесена из Compose и MapKit, чтобы её можно было проверить
 * unit-тестами без нативной библиотеки карты.
 */
object TrafficPolicy {

    /**
     * Тип подложки, на которой рисуется карта.
     *
     * Раньше приложение принудительно ставило [MapType.MAP] — устаревшую растровую
     * подложку вместо векторной, которую MapKit 4.x использует по умолчанию.
     * На ней не работает ночной режим (`setNightModeEnabled`), а слой пробок MapKit
     * 4.x — векторный (`vtrf`). Причиной отсутствия цвета это не было (на эмуляторе
     * запрос слоя пробок получал 403 при обеих подложках), но векторная схема —
     * штатный режим SDK. Переключателя типа карты в интерфейсе нет, а «Спутник»
     * и «Гибрид» по условиям MapKit разрешены только приложениям Яндекса, поэтому
     * любое сохранённое ранее значение сводится к векторной схеме.
     */
    fun effectiveMapType(@Suppress("UNUSED_PARAMETER") stored: MapType?): MapType = MapType.VECTOR_MAP

    /** Через сколько миллисекунд без ответа MapKit честно сказать, что данных нет. */
    const val NO_DATA_TIMEOUT_MS: Long = 20_000L

    /** Что сообщил MapKit через TrafficListener. */
    enum class SdkState { IDLE, LOADING, READY, EXPIRED }

    /** Цвет общего балла пробок по данным Яндекса. */
    enum class LevelColor { GREEN, YELLOW, RED }

    enum class Kind { OFF, NO_NETWORK, ACCESS_DENIED, LOADING, NO_DATA, READY_WITH_LEVEL, READY_WITHOUT_LEVEL, EXPIRED }

    data class Status(
        val kind: Kind,
        /** Балл пробок 0–10, если Яндекс его рассчитывает для этого города. */
        val level: Int? = null,
        val levelColor: LevelColor? = null
    ) {
        val message: String?
            get() = when (kind) {
                Kind.OFF -> null
                Kind.NO_NETWORK -> "Пробки недоступны: нет интернета"
                Kind.ACCESS_DENIED ->
                    "Яндекс отказал в доступе к слою пробок (ошибка 403). Цвет дорог не показан"
                Kind.LOADING -> "Пробки: загружаем данные Яндекса…"
                Kind.NO_DATA -> "Яндекс не прислал данные о пробках. Цвет дорог не показан"
                Kind.READY_WITH_LEVEL -> "Пробки: ${level ?: 0} ${pointsWord(level ?: 0)} по данным Яндекса"
                Kind.READY_WITHOUT_LEVEL ->
                    "Слой пробок Яндекса включён. Общего балла пробок для Норильска Яндекс не даёт"
                Kind.EXPIRED -> "Данные о пробках устарели, обновляем…"
            }
    }

    /**
     * Сводит включённость слоя, сеть и последнее событие MapKit в один статус.
     * Без данных цвет никогда не дорисовывается — только честный текст.
     */
    fun status(
        enabled: Boolean,
        networkAvailable: Boolean,
        sdkState: SdkState,
        level: Int?,
        levelColor: LevelColor?,
        timedOut: Boolean,
        accessDenied: Boolean = false
    ): Status = when {
        !enabled -> Status(Kind.OFF)
        !networkAvailable -> Status(Kind.NO_NETWORK)
        // Сервер отклонил векторный слой пробок: балл может приходить, а цвета на
        // дорогах не будет — об этом нужно сказать прямо, а не показывать «N баллов».
        accessDenied -> Status(Kind.ACCESS_DENIED, level, levelColor)
        sdkState == SdkState.READY && level != null -> Status(Kind.READY_WITH_LEVEL, level, levelColor)
        sdkState == SdkState.READY -> Status(Kind.READY_WITHOUT_LEVEL)
        sdkState == SdkState.EXPIRED -> Status(Kind.EXPIRED)
        timedOut -> Status(Kind.NO_DATA)
        else -> Status(Kind.LOADING)
    }

    /**
     * MapKit не сообщает через API, что сервер отказал в слое пробок: балл
     * (TrafficListener) приходит из другого сервиса, а запрос векторного слоя
     * `vtrf` получает HTTP 403 и пишет об этом только в logcat (тег yandex.maps).
     * По этим строкам приложение и узнаёт, почему дороги не окрашены.
     */
    fun isTrafficLayerForbidden(logLines: List<String>): Boolean {
        for ((i, line) in logLines.withIndex()) {
            if (!line.contains("Could not update layer") || !line.contains("/vtrf")) continue
            // Тело ответа nginx MapKit печатает отдельными строками прямо перед этой.
            val from = (i - 12).coerceAtLeast(0)
            if ((from..i).any { j -> logLines[j].contains("403") || logLines[j].contains("Forbidden") }) return true
        }
        return false
    }

    /** «1 балл», «2 балла», «5 баллов», «11 баллов». */
    fun pointsWord(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> "баллов"
            mod10 == 1 -> "балл"
            mod10 in 2..4 -> "балла"
            else -> "баллов"
        }
    }

    /** Толщина и прозрачность линий маршрута поверх карты. */
    data class RouteStroke(
        val outlineColor: Int,
        val outlineWidth: Float,
        val lineAlpha: Int,
        val lineWidth: Float
    )

    /**
     * Линии автобусных маршрутов — объекты карты, они всегда рисуются над слоем
     * пробок. Непрозрачная полоса 8 px с чёрной окантовкой лежала ровно на той
     * же улице и закрывала её цвет. При включённых пробках маршрут становится
     * тоньше и полупрозрачным, без чёрной окантовки, и цвет дороги виден по краям
     * и сквозь линию.
     */
    fun routeStroke(trafficVisible: Boolean, journey: Boolean = false): RouteStroke =
        if (trafficVisible) {
            RouteStroke(
                outlineColor = 0x00000000,
                outlineWidth = 0f,
                lineAlpha = 0x99,
                lineWidth = 3f
            )
        } else if (journey) {
            RouteStroke(outlineColor = 0xFF000000.toInt(), outlineWidth = 9f, lineAlpha = 0xFF, lineWidth = 5f)
        } else {
            RouteStroke(outlineColor = 0xFF000000.toInt(), outlineWidth = 8f, lineAlpha = 0xFF, lineWidth = 4f)
        }

    /** Цвет маршрута [argb] с альфой [alpha] (0–255). */
    fun withAlpha(argb: Int, alpha: Int): Int = (argb and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
