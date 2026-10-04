// Models.swift — модели данных приложения.
//
// Слой не зависит от UIKit/MapKit/SwiftUI: только Foundation.
// Это позволяет покрывать парсинг и логику расписаний unit-тестами
// без запуска приложения (и без инициализации MapKit).

import Foundation

// MARK: - Геометрия

public struct LatLon: Hashable, Codable {
    public let lat: Double
    public let lon: Double
}

// MARK: - Остановка

public struct Stop: Identifiable, Hashable, Codable {
    public let id: Int
    public let name: String
    public let lat: Double
    public let lon: Double
}

// MARK: - Маршрут (направление с геометрией)

/// Одно направление маршрута: id вида "2246:2" (busId:directionIndex).
public struct Route: Identifiable, Hashable, Codable {
    public let id: String
    public let busId: Int
    public let number: String
    public let slug: String
    public let direction: Int
    public let origin: String
    public let destination: String
    public let colorArgb: UInt32
    public let stops: [Stop]
    public let polyline: [LatLon]

    private enum CodingKeys: String, CodingKey {
        case id, busId, number, slug, direction, origin, destination, colorArgb, stops, polyline
    }

    /// Значения по умолчанию только для технических полей (slug, color).
    /// `id` и `number` обязательны: без них маршрут не имеет смысла,
    /// и такой файл должен падать на разборе, а не молча давать пустые строки.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        number = try c.decode(String.self, forKey: .number)
        busId = try c.decodeIfPresent(Int.self, forKey: .busId) ?? 0
        slug = try c.decodeIfPresent(String.self, forKey: .slug) ?? ""
        direction = try c.decodeIfPresent(Int.self, forKey: .direction) ?? 0
        origin = try c.decodeIfPresent(String.self, forKey: .origin) ?? ""
        destination = try c.decodeIfPresent(String.self, forKey: .destination) ?? ""
        colorArgb = try c.decodeIfPresent(UInt32.self, forKey: .colorArgb) ?? 0xFF_8E_8E_93
        stops = try c.decodeIfPresent([Stop].self, forKey: .stops) ?? []
        polyline = try c.decodeIfPresent([LatLon].self, forKey: .polyline) ?? []
    }
}

// MARK: - Расписание

public struct TimetableEntry: Hashable, Codable, Identifiable {
    public let terminal: String
    public let weekday: [String]
    public let weekend: [String]

    public var id: String { terminal }

    private enum CodingKeys: String, CodingKey {
        case terminal, weekday, weekend
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        terminal = try c.decode(String.self, forKey: .terminal)
        weekday = try c.decodeIfPresent([String].self, forKey: .weekday) ?? []
        weekend = try c.decodeIfPresent([String].self, forKey: .weekend) ?? []
    }
}

public struct ScheduleEntry: Hashable, Codable {
    public let number: String
    public let hasSchedule: Bool
    public let timetable: [TimetableEntry]

    /// Дублирующие поля из старого формата файла (могут отсутствовать).
    public let terminal: String?
    public let weekday: [String]?
    public let weekend: [String]?

    /// Терминалы, по которым есть времена отправления.
    public var terminals: [String] { timetable.map { $0.terminal } }

    private enum CodingKeys: String, CodingKey {
        case number, hasSchedule, timetable, terminal, weekday, weekend
    }

    /// Расписание публиковалось в двух форматах: новый (timetable по терминалам)
    /// и старый (terminal/weekday/weekend на верхнем уровне). Понимаем оба,
    /// но не выдумываем данных: чего в файле нет — того нет.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        number = try c.decode(String.self, forKey: .number)
        hasSchedule = try c.decodeIfPresent(Bool.self, forKey: .hasSchedule) ?? true
        timetable = try c.decodeIfPresent([TimetableEntry].self, forKey: .timetable) ?? []
        terminal = try c.decodeIfPresent(String.self, forKey: .terminal)
        weekday = try c.decodeIfPresent([String].self, forKey: .weekday)
        weekend = try c.decodeIfPresent([String].self, forKey: .weekend)
    }
}

public struct ScheduleDatabase: Codable {
    public let dataDate: String?
    public let routes: [String: ScheduleEntry]
}

// MARK: - Вариант маршрута (объединённое представление)

/// Направление маршрута в том виде, в котором его показывает приложение.
///
/// Важно: вариант может быть
///  - с геометрией (есть запись в norilsk_routes.json),
///  - только с расписанием (есть запись только в norilsk_schedule.json).
/// Второй случай — например 31Э: геометрия от МУП не опубликована,
/// поэтому мы НЕ выдумываем линию, а честно показываем отсутствие геометрии.
public struct RouteVariant: Identifiable, Hashable {
    public let id: String
    public let number: String
    public let direction: Int
    public let colorArgb: UInt32
    public let origin: String?
    public let destination: String?
    public let stops: [Stop]
    public let polyline: [LatLon]
    public let hasGeometry: Bool
    public let schedule: ScheduleEntry?

    public var title: String {
        if let origin = origin, let destination = destination, hasGeometry {
            return "\(origin) → \(destination)"
        }
        let terminals = schedule?.terminals ?? []
        if !terminals.isEmpty {
            return "терминалы: " + terminals.joined(separator: " · ")
        }
        return "направление \(id)"
    }

    public var hasSchedule: Bool { schedule?.hasSchedule ?? false }

    public var isStarredHint: Bool { hasGeometry }
}

// MARK: - Остановка в индексе

public struct StopInfo: Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let lat: Double
    public let lon: Double
    public let routeIds: [String]
}
