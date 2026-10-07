package com.example.data.network

/** Уровень опасности дорожных условий. */
enum class RoadSeverity { NORMAL, CAUTION, DANGER }

data class RoadConditions(
    val severity: RoadSeverity,
    val title: String,
    val factors: List<String>,
    val recommendation: String
)

/**
 * Оценка дорожных условий ПРЯМО в телефоне из текущей погоды Open-Meteo
 * (сервер не нужен). Эвристика под Норильск: гололёд / метель-заносы / мороз / видимость.
 */
fun assessRoadConditions(w: WeatherUi): RoadConditions {
    val t = w.temp
    val feels = w.feelsLike
    val wind = w.windSpeed
    val desc = w.description.lowercase()

    val snow = listOf("снег", "метел", "пург").any { desc.contains(it) }
    val rain = listOf("дожд", "морос", "ливень").any { desc.contains(it) }
    val fog = listOf("туман", "дымк", "мгла").any { desc.contains(it) }
    val freezing = listOf("ледян", "гололёд", "гололед", "изморозь").any { desc.contains(it) }

    val factors = mutableListOf<String>()
    var severity = RoadSeverity.NORMAL

    // Гололёд: около нуля + осадки/влага, либо явный ледяной дождь
    if ((t in -6.0..2.0 && (snow || rain)) || freezing) {
        factors.add("❄️ Возможен гололёд (температура около нуля)")
        severity = higher(severity, RoadSeverity.DANGER)
    }
    // Метель/заносы: сильный ветер + снег
    if (wind >= 11.0 && (snow || t < -2.0)) {
        factors.add("🌬️ Метель и снежные заносы (ветер ${wind.toInt()} м/с)")
        severity = higher(severity, RoadSeverity.DANGER)
    } else if (wind >= 8.0) {
        factors.add("💨 Усиление ветра до ${wind.toInt()} м/с")
        severity = higher(severity, RoadSeverity.CAUTION)
    }
    // Сильный мороз
    if (feels <= -35.0) {
        factors.add("🥶 Сильный мороз: ощущается ${feels.toInt()}°C")
        severity = higher(severity, RoadSeverity.CAUTION)
    }
    // Видимость
    if (fog || (snow && wind >= 11.0)) {
        factors.add("🌫️ Ограниченная видимость")
        severity = higher(severity, RoadSeverity.CAUTION)
    }
    // Лёгкий снег без ветра
    if (snow && wind < 11.0 && severity == RoadSeverity.NORMAL) {
        factors.add("🌨️ Снег — дорога может быть скользкой")
        severity = RoadSeverity.CAUTION
    }

    val (title, recommendation) = when (severity) {
        RoadSeverity.DANGER -> "Опасные дорожные условия" to
            "Соблюдайте дистанцию, снизьте скорость, избегайте резких манёвров. Закладывайте время на дорогу."
        RoadSeverity.CAUTION -> "Будьте внимательны на дороге" to
            "Возможна скользкость. Двигайтесь аккуратно, увеличьте дистанцию."
        RoadSeverity.NORMAL -> "Дорожные условия в норме" to
            "Существенных погодных рисков нет. Хорошей дороги!"
    }
    if (factors.isEmpty()) factors.add("✅ Существенных рисков по погоде не выявлено")

    return RoadConditions(severity, title, factors, recommendation)
}

private fun higher(a: RoadSeverity, b: RoadSeverity): RoadSeverity =
    if (a.ordinal >= b.ordinal) a else b
