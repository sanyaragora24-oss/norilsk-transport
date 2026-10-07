package com.example.data.network

/**
 * Штормовые предупреждения по погоде.
 *
 * Пороги взяты из критериев опасных (ОЯ) и неблагоприятных (НЯ) явлений
 * Росгидромета и адаптированы под Норильск: здесь ветер 15 м/с со снегом —
 * рядовая зимняя погода, а вот пурга с видимостью менее 500 м уже приводит к
 * закрытию автодорог между районами (решение штаба «Шторм»), и автобусы
 * Норильск — Талнах — Кайеркан — Алыкель в этот момент не ходят.
 *
 * Логика намеренно не зависит от Android: её можно прогонять unit-тестами.
 */

enum class WeatherAlertLevel {
    /** Обратите внимание: одеться теплее, заложить время. */
    ADVISORY,

    /** Неблагоприятное явление: поездка осложнена. */
    WARNING,

    /** Опасное явление: возможна отмена рейсов, лучше остаться дома. */
    SEVERE
}

enum class WeatherAlertKind { BLIZZARD, WIND, FROST, ICE, VISIBILITY }

data class WeatherAlert(
    val kind: WeatherAlertKind,
    val level: WeatherAlertLevel,
    val title: String,
    val message: String,
    val advice: String,
    /** 0 — явление уже идёт; >0 — ожидается через столько часов. */
    val hoursAhead: Int = 0
) {
    /**
     * Ключ для дедупликации уведомлений: пока погода не сменила категорию,
     * пассажир не должен получать одно и то же предупреждение повторно.
     */
    val id: String get() = "${kind.name}_${level.name}_" + if (hoursAhead == 0) "now" else "in$hoursAhead"

    val isForecast: Boolean get() = hoursAhead > 0
}

// --- Пороги ---------------------------------------------------------------

private const val WIND_SEVERE = 25.0      // ОЯ «очень сильный ветер»
private const val GUST_SEVERE = 30.0
private const val WIND_WARNING = 15.0     // НЯ «сильный ветер»
private const val GUST_WARNING = 20.0
private const val WIND_ADVISORY = 8.0

private const val BLIZZARD_WIND = 11.0    // метель начинается с позёмка при таком ветре
private const val VISIBILITY_SEVERE = 500.0
private const val VISIBILITY_WARNING = 1000.0

private const val FEELS_SEVERE = -45.0
private const val FEELS_WARNING = -35.0
private const val FEELS_ADVISORY = -25.0

private fun String.hasSnow() = listOf("снег", "метел", "пург", "крупа").any { contains(it) }
private fun String.hasRain() = listOf("дожд", "морос", "ливень").any { contains(it) }
private fun String.hasFog() = listOf("туман", "дымк", "мгла").any { contains(it) }
private fun String.hasFreezing() =
    listOf("ледян", "гололёд", "гололед", "изморозь").any { contains(it) }

/**
 * Оценивает текущую погоду и ближайший прогноз.
 *
 * Возвращает список предупреждений, отсортированный по важности: сначала то,
 * что происходит сейчас и опаснее всего. Для каждого вида явления остаётся
 * только одно, самое серьёзное предупреждение — чтобы не заваливать пассажира
 * пятью карточками об одном и том же ветре.
 */
fun evaluateWeatherAlerts(weather: WeatherUi, maxForecastHours: Int = 6): List<WeatherAlert> {
    val now = alertsForConditions(
        temp = weather.temp,
        feelsLike = weather.feelsLike,
        wind = weather.windSpeed,
        gusts = weather.windGusts,
        visibility = weather.visibilityMeters,
        description = weather.description,
        hoursAhead = 0
    )

    // Из прогноза берём только то, что серьёзнее уже идущего: предупреждать
    // «через 3 часа будет ветер», когда он дует прямо сейчас, бессмысленно.
    val worstNow = now.maxOfOrNull { it.level.ordinal } ?: -1
    val forecast = weather.hourly
        .filter { it.hoursFromNow in 1..maxForecastHours }
        .sortedBy { it.hoursFromNow }
        .flatMap { hour ->
            alertsForConditions(
                temp = hour.temp,
                feelsLike = hour.feelsLike,
                wind = hour.windSpeed,
                gusts = hour.windGusts,
                visibility = hour.visibilityMeters,
                description = hour.description,
                hoursAhead = hour.hoursFromNow
            )
        }
        .filter { it.level.ordinal > worstNow }

    return (now + forecast)
        .groupBy { it.kind }
        // по каждому явлению — самое серьёзное, а при равном уровне — самое раннее
        .map { (_, alerts) ->
            alerts.sortedWith(
                compareByDescending<WeatherAlert> { it.level.ordinal }.thenBy { it.hoursAhead }
            ).first()
        }
        .sortedWith(
            compareByDescending<WeatherAlert> { it.level.ordinal }.thenBy { it.hoursAhead }
        )
}

private fun alertsForConditions(
    temp: Double?,
    feelsLike: Double?,
    wind: Double?,
    gusts: Double?,
    visibility: Double?,
    description: String,
    hoursAhead: Int
): List<WeatherAlert> {
    val alerts = mutableListOf<WeatherAlert>()
    val desc = description.lowercase()
    val snow = desc.hasSnow()
    val w = wind ?: 0.0
    val g = gusts ?: 0.0
    val windPeak = maxOf(w, g)

    val whenText = if (hoursAhead == 0) "сейчас" else "через ${hoursText(hoursAhead)}"

    // Пиковое значение чаще всего приходит из порывов. Если не назвать это
    // порывом, число в предупреждении будет противоречить средней скорости
    // ветра в шапке приложения, и пассажир решит, что где-то ошибка.
    val windText = if (g > w) "порывы до ${g.toInt()} м/с" else "ветер ${w.toInt()} м/с"

    // --- Пурга: главный транспортный риск Норильска ---
    val lowVisibility = visibility != null && visibility <= VISIBILITY_SEVERE
    if (snow && (windPeak >= WIND_WARNING || (windPeak >= BLIZZARD_WIND && lowVisibility))) {
        val severe = windPeak >= WIND_SEVERE || (windPeak >= WIND_WARNING && lowVisibility)
        alerts += if (severe) {
            WeatherAlert(
                kind = WeatherAlertKind.BLIZZARD,
                level = WeatherAlertLevel.SEVERE,
                title = if (hoursAhead == 0) "Пурга" else "Ожидается пурга",
                message = "Метель, $windText" +
                    (visibility?.let { ", видимость около ${it.toInt()} м" } ?: "") +
                    ", $whenText.",
                advice = "Автодороги между районами (Талнах, Кайеркан, Алыкель) в такую " +
                    "погоду могут закрыть, междугородние рейсы — отменить. " +
                    "Проверьте сообщения перед выходом и по возможности отложите поездку.",
                hoursAhead = hoursAhead
            )
        } else {
            WeatherAlert(
                kind = WeatherAlertKind.BLIZZARD,
                level = WeatherAlertLevel.WARNING,
                title = if (hoursAhead == 0) "Метель" else "Ожидается метель",
                message = "Снег, $windText $whenText.",
                advice = "Автобусы могут идти с опозданием. Заложите на дорогу больше времени " +
                    "и ждите транспорт в укрытии.",
                hoursAhead = hoursAhead
            )
        }
    } else if (windPeak >= WIND_SEVERE || g >= GUST_SEVERE) {
        // --- Сильный ветер без снега ---
        alerts += WeatherAlert(
            kind = WeatherAlertKind.WIND,
            level = WeatherAlertLevel.SEVERE,
            title = if (hoursAhead == 0) "Очень сильный ветер" else "Ожидается очень сильный ветер",
            message = "${windText.replaceFirstChar { it.uppercase() }} $whenText.",
            advice = "Опасно находиться у зданий и рекламных конструкций. " +
                "Движение транспорта может быть ограничено.",
            hoursAhead = hoursAhead
        )
    } else if (windPeak >= WIND_WARNING || g >= GUST_WARNING) {
        alerts += WeatherAlert(
            kind = WeatherAlertKind.WIND,
            level = WeatherAlertLevel.WARNING,
            title = if (hoursAhead == 0) "Сильный ветер" else "Ожидается сильный ветер",
            message = "${windText.replaceFirstChar { it.uppercase() }} $whenText.",
            advice = "На открытых участках идти тяжело. Ждите автобус в укрытии.",
            hoursAhead = hoursAhead
        )
    } else if (windPeak >= WIND_ADVISORY && hoursAhead == 0) {
        alerts += WeatherAlert(
            kind = WeatherAlertKind.WIND,
            level = WeatherAlertLevel.ADVISORY,
            title = "Усиление ветра",
            message = "${windText.replaceFirstChar { it.uppercase() }}.",
            advice = "На остановке будет ощутимо холоднее, чем показывает градусник.",
            hoursAhead = hoursAhead
        )
    }

    // --- Мороз: важно, потому что пассажир ждёт автобус на улице ---
    val feels = feelsLike ?: temp
    if (feels != null) {
        when {
            feels <= FEELS_SEVERE -> alerts += WeatherAlert(
                kind = WeatherAlertKind.FROST,
                level = WeatherAlertLevel.SEVERE,
                title = if (hoursAhead == 0) "Аномальный мороз" else "Ожидается аномальный мороз",
                message = "Ощущается как ${feels.toInt()} °C $whenText.",
                advice = "Открытая кожа обмораживается за считанные минуты. " +
                    "Без крайней необходимости не выходите; ждите транспорт только в помещении.",
                hoursAhead = hoursAhead
            )

            feels <= FEELS_WARNING -> alerts += WeatherAlert(
                kind = WeatherAlertKind.FROST,
                level = WeatherAlertLevel.WARNING,
                title = if (hoursAhead == 0) "Сильный мороз" else "Ожидается сильный мороз",
                message = "Ощущается как ${feels.toInt()} °C $whenText.",
                advice = "Закройте лицо, не ждите автобус на открытом месте дольше 10 минут.",
                hoursAhead = hoursAhead
            )

            feels <= FEELS_ADVISORY && hoursAhead == 0 -> alerts += WeatherAlert(
                kind = WeatherAlertKind.FROST,
                level = WeatherAlertLevel.ADVISORY,
                title = "Мороз",
                message = "Ощущается как ${feels.toInt()} °C.",
                advice = "Оденьтесь теплее — на остановке придётся стоять на улице.",
                hoursAhead = hoursAhead
            )
        }
    }

    // --- Гололёд ---
    val nearZero = temp != null && temp in -6.0..2.0
    if (desc.hasFreezing() || (nearZero && (snow || desc.hasRain()))) {
        alerts += WeatherAlert(
            kind = WeatherAlertKind.ICE,
            level = WeatherAlertLevel.WARNING,
            title = if (hoursAhead == 0) "Гололёд" else "Ожидается гололёд",
            message = "Температура около нуля с осадками $whenText.",
            advice = "Скользко на тротуарах и у остановок. Держитесь за поручни в салоне.",
            hoursAhead = hoursAhead
        )
    }

    // --- Видимость (отдельно от пурги: бывает туман без ветра) ---
    if (visibility != null && !snow) {
        when {
            visibility <= VISIBILITY_SEVERE -> alerts += WeatherAlert(
                kind = WeatherAlertKind.VISIBILITY,
                level = WeatherAlertLevel.WARNING,
                title = if (hoursAhead == 0) "Очень плохая видимость" else "Ожидается плохая видимость",
                message = "Видимость около ${visibility.toInt()} м $whenText.",
                advice = "Водитель может не заметить вас на остановке — стойте на виду, " +
                    "по возможности с включённым фонариком телефона.",
                hoursAhead = hoursAhead
            )

            visibility <= VISIBILITY_WARNING && desc.hasFog() -> alerts += WeatherAlert(
                kind = WeatherAlertKind.VISIBILITY,
                level = WeatherAlertLevel.ADVISORY,
                title = "Туман",
                message = "Видимость около ${visibility.toInt()} м $whenText.",
                advice = "Транспорт идёт медленнее обычного, возможны опоздания.",
                hoursAhead = hoursAhead
            )
        }
    }

    return alerts
}

private fun hoursText(hours: Int): String = when {
    hours % 10 == 1 && hours % 100 != 11 -> "$hours час"
    hours % 10 in 2..4 && hours % 100 !in 12..14 -> "$hours часа"
    else -> "$hours часов"
}
