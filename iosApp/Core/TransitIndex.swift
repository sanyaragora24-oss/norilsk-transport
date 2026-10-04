// TransitIndex.swift — объединение маршрутов и расписаний в поисковый индекс.
//
// Правила объединения (важно для 31/31Э):
//  1. Каждая запись norilsk_routes.json даёт вариант С геометрией.
//  2. Ключи norilsk_schedule.json, которых нет в routes.json (например 31Э и
//     старые направления 31), дают вариант БЕЗ геометрии. Мы их показываем,
//     но не выдумываем для них линию и остановки.
//  3. Ничего не удаляем и не объединяем: все направления остаются отдельными.

import Foundation

public struct TransitIndex {
    public let dataDate: String?
    public let variants: [RouteVariant]
    public let stops: [StopInfo]
    public let numbers: [String]

    private let variantsById: [String: RouteVariant]
    private let stopsById: [Int: StopInfo]
    private let variantIdsByStop: [Int: [String]]
    private let variantsByNumber: [String: [RouteVariant]]

    public init(routes: [Route], schedule: ScheduleDatabase) {
        self.dataDate = schedule.dataDate

        let routesById = Dictionary(uniqueKeysWithValues: routes.map { ($0.id, $0) })
        let schedules = schedule.routes

        var result: [RouteVariant] = []

        // 1. Направления с геометрией
        for route in routes {
            result.append(
                RouteVariant(
                    id: route.id,
                    number: route.number,
                    direction: route.direction,
                    colorArgb: route.colorArgb,
                    origin: route.origin,
                    destination: route.destination,
                    stops: route.stops,
                    polyline: route.polyline,
                    hasGeometry: true,
                    schedule: schedules[route.id]
                )
            )
        }

        // 2. Направления, у которых есть только расписание (геометрия не опубликована)
        for key in schedules.keys.sorted() where routesById[key] == nil {
            let entry = schedules[key]!
            let direction = Self.directionFromId(key) ?? -1
            result.append(
                RouteVariant(
                    id: key,
                    number: entry.number,
                    direction: direction,
                    // Нейтральный серый: своего цвета у таких направлений в данных нет,
                    // это чисто презентационное значение.
                    colorArgb: 0xFF_8E_8E_93,
                    origin: nil,
                    destination: nil,
                    stops: [],
                    polyline: [],
                    hasGeometry: false,
                    schedule: entry
                )
            )
        }

        result.sort { lhs, rhs in
            let lk = Self.numberSortKey(lhs.number)
            let rk = Self.numberSortKey(rhs.number)
            if lk.digits != rk.digits { return lk.digits < rk.digits }
            if lk.letters != rk.letters { return lk.letters < rk.letters }
            if lhs.direction != rhs.direction { return lhs.direction < rhs.direction }
            return lhs.id < rhs.id
        }

        self.variants = result
        self.variantsById = Dictionary(uniqueKeysWithValues: result.map { ($0.id, $0) })

        var byNumber: [String: [RouteVariant]] = [:]
        for variant in result { byNumber[variant.number, default: []].append(variant) }
        self.variantsByNumber = byNumber
        self.numbers = byNumber.keys.sorted {
            let lk = Self.numberSortKey($0)
            let rk = Self.numberSortKey($1)
            return lk.digits != rk.digits ? lk.digits < rk.digits : lk.letters < rk.letters
        }

        // 3. Индекс остановок (только по направлениям с геометрией)
        var stopsById: [Int: StopInfo] = [:]
        var variantIdsByStop: [Int: [String]] = [:]
        for route in routes {
            for stop in route.stops {
                variantIdsByStop[stop.id, default: []].append(route.id)
                if stopsById[stop.id] == nil {
                    stopsById[stop.id] = StopInfo(
                        id: stop.id,
                        name: stop.name,
                        lat: stop.lat,
                        lon: stop.lon,
                        routeIds: []
                    )
                }
            }
        }
        for (stopId, ids) in variantIdsByStop {
            // Порядок сохраняем, дубликаты (одинаковое направление в двух записях) убираем
            var seen = Set<String>()
            let unique = ids.filter { seen.insert($0).inserted }
            variantIdsByStop[stopId] = unique
            if let info = stopsById[stopId] {
                stopsById[stopId] = StopInfo(
                    id: stopId,
                    name: info.name,
                    lat: info.lat,
                    lon: info.lon,
                    routeIds: unique
                )
            }
        }

        self.stopsById = stopsById
        self.variantIdsByStop = variantIdsByStop
        self.stops = stopsById.values.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    // MARK: - Запросы

    public func variant(id: String) -> RouteVariant? { variantsById[id] }

    public func variants(number: String) -> [RouteVariant] { variantsByNumber[number] ?? [] }

    public func stop(id: Int) -> StopInfo? { stopsById[id] }

    public func routesThrough(stopId: Int) -> [RouteVariant] {
        (variantIdsByStop[stopId] ?? []).compactMap { variantsById[$0] }
    }

    public func searchRoutes(_ query: String) -> [RouteVariant] {
        let q = normalized(query)
        guard !q.isEmpty else { return variants }
        return variants.filter { variant in
            normalized(variant.number).contains(q)
                || normalized(variant.title).contains(q)
                || (variant.origin.map { normalized($0).contains(q) } ?? false)
                || (variant.destination.map { normalized($0).contains(q) } ?? false)
        }
    }

    public func searchStops(_ query: String) -> [StopInfo] {
        let q = normalized(query)
        guard !q.isEmpty else { return stops }
        return stops.filter { normalized($0.name).contains(q) }
    }

    /// Ближайшие остановки по прямой (гаверсинус), без привязки к дорогам.
    public func nearestStops(latitude: Double, longitude: Double, limit: Int = 10) -> [(stop: StopInfo, distance: Double)] {
        stops
            .map { (stop: $0, distance: Geo.distanceMeters(fromLat: latitude, fromLon: longitude, toLat: $0.lat, toLon: $0.lon)) }
            .sorted { $0.distance < $1.distance }
            .prefix(limit)
            .map { $0 }
    }

    // MARK: - Вспомогательное

    /// "2246:2" -> 2
    public static func directionFromId(_ id: String) -> Int? {
        guard let suffix = id.split(separator: ":").last else { return nil }
        return Int(suffix)
    }

    /// Естественная сортировка номеров: "2" < "11" < "1А" < "22" < "22И".
    public static func numberSortKey(_ number: String) -> (digits: Int, letters: String) {
        let digits = number.prefix(while: { $0.isNumber })
        let letters = String(number.dropFirst(digits.count))
        return (Int(digits) ?? Int.max, letters)
    }

    private func normalized(_ value: String) -> String {
        value
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .replacingOccurrences(of: "ё", with: "е")
            .replacingOccurrences(of: "№", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

// MARK: - Геодезия

public enum Geo {
    /// Длина ломаной по точкам трека, в метрах (сумма отрезков по сфере).
    public static func polylineLengthMeters(_ points: [LatLon]) -> Double {
        guard points.count >= 2 else { return 0 }
        var total = 0.0
        for index in 1..<points.count {
            let previous = points[index - 1]
            let current = points[index]
            total += distanceMeters(fromLat: previous.lat, fromLon: previous.lon,
                                    toLat: current.lat, toLon: current.lon)
        }
        return total
    }

    /// Расстояние между двумя точками на сфере, в метрах.
    public static func distanceMeters(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) -> Double {
        let radius = 6_371_000.0
        let dLat = (toLat - fromLat) * .pi / 180
        let dLon = (toLon - fromLon) * .pi / 180
        let lat1 = fromLat * .pi / 180
        let lat2 = toLat * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        let c = 2 * atan2(sqrt(a), sqrt(max(0, 1 - a)))
        return radius * c
    }
}
