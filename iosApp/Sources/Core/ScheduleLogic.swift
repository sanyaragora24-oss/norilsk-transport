// ScheduleLogic.swift — работа со временем расписания.
//
// Все времена в данных МУП — строки вида "HH:mm" (локальное время Норильска).
// Мы НЕ интерпретируем их как UTC и не пересчитываем в другие часовые пояса:
// расписание показывается ровно так, как опубликовано.

import Foundation

public enum ScheduleLogic {
    /// "07:20" -> 440 (минуты от начала суток). Возвращает nil для мусорных строк.
    public static func parseTime(_ value: String) -> Int? {
        let parts = value.split(separator: ":")
        guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
        guard (0..<24).contains(h), (0..<60).contains(m) else { return nil }
        return h * 60 + m
    }

    /// 440 -> "07:20"
    public static func formatMinutes(_ minutes: Int) -> String {
        let h = minutes / 60
        let m = minutes % 60
        return String(format: "%02d:%02d", h, m)
    }

    /// Минуты от начала суток для переданной даты в указанном календаре.
    public static func minutes(of date: Date, calendar: Calendar = .current) -> Int {
        let comps = calendar.dateComponents([.hour, .minute], from: date)
        return (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
    }

    /// Индекс дня недели (1 = воскресенье … 7 = суббота), как в Calendar.component(.weekday).
    public static func isWeekend(weekdayIndex: Int) -> Bool {
        weekdayIndex == 1 || weekdayIndex == 7
    }

    public static func isWeekend(_ date: Date, calendar: Calendar = .current) -> Bool {
        isWeekend(weekdayIndex: calendar.component(.weekday, from: date))
    }

    /// Времена отправления, отсортированные по возрастанию, без повторов и без мусора.
    public static func normalizedTimes(_ times: [String]) -> [Int] {
        var seen = Set<Int>()
        var result: [Int] = []
        for raw in times {
            guard let minutes = parseTime(raw), !seen.contains(minutes) else { continue }
            seen.insert(minutes)
            result.append(minutes)
        }
        return result.sorted()
    }

    /// Ближайшие отправления НЕ РАНЕЕ текущего момента, в пределах тех же суток.
    ///
    /// Если на сегодня отправлений больше нет — возвращается пустой массив:
    /// мы не переносим время на завтра, потому что в данных МУП
    /// нет подтверждённого расписания следующего дня.
    public static func nextDepartures(
        times: [String],
        nowMinutes: Int,
        limit: Int = 5
    ) -> [Int] {
        let sorted = normalizedTimes(times)
        let upcoming = sorted.filter { $0 >= nowMinutes }
        return Array(upcoming.prefix(limit))
    }

    /// Сколько минут осталось до отправления.
    public static func minutesUntil(departure: Int, nowMinutes: Int) -> Int {
        departure - nowMinutes
    }

    /// Норильск живёт по красноярскому времени (UTC+7, без перевода часов).
    /// Расписание опубликовано в местном времени, поэтому «сейчас» считаем
    /// в норильской зоне, а не в зоне устройства.
    public static func norilskTimeZone() -> TimeZone {
        TimeZone(identifier: "Asia/Krasnoyarsk") ?? TimeZone(secondsFromGMT: 7 * 3600) ?? .current
    }

    public static func norilskCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = norilskTimeZone()
        return calendar
    }

    public static func minutesNow(_ date: Date = Date()) -> Int {
        minutes(of: date, calendar: norilskCalendar())
    }

    public static func isWeekendNow(_ date: Date = Date()) -> Bool {
        isWeekend(date, calendar: norilskCalendar())
    }

    /// Человекочитаемый остаток: "через 12 мин", "через 1 ч 05 мин".
    public static func countdownText(departure: Int, nowMinutes: Int) -> String {
        let delta = minutesUntil(departure: departure, nowMinutes: nowMinutes)
        if delta <= 0 { return "сейчас" }
        if delta < 60 { return "через \(delta) мин" }
        let h = delta / 60
        let m = delta % 60
        if m == 0 { return "через \(h) ч" }
        return "через \(h) ч \(String(format: "%02d", m)) мин"
    }
}
