// MapOverlays.swift — что именно рисуется на карте.
//
// Логика вынесена из MapScreen в Core (только Foundation, без UIKit/MapKit),
// чтобы её можно было проверить unit-тестами на настоящих production-данных:
// сколько линий рисуется без выбора и при выборе, какие остановки попадают на
// карту, меняется ли подпись перерисовки. Визуально в CI это не проверить.

import Foundation

/// Линия маршрута на карте. Цвет хранится как ARGB, а не UIColor:
/// Core не зависит от UIKit, UIColor создаётся уже в слое View.
public struct MapRouteOverlay: Equatable {
    public let id: String
    public let points: [LatLon]
    public let colorArgb: UInt32
    public let isSelected: Bool

    public init(id: String, points: [LatLon], colorArgb: UInt32, isSelected: Bool) {
        self.id = id
        self.points = points
        self.colorArgb = colorArgb
        self.isSelected = isSelected
    }
}

public struct MapStopOverlay: Equatable {
    public let id: Int
    public let lat: Double
    public let lon: Double
    public let title: String

    public init(id: Int, lat: Double, lon: Double, title: String) {
        self.id = id
        self.lat = lat
        self.lon = lon
        self.title = title
    }
}

public enum MapOverlays {

    /// Линии для карты:
    ///  - выбран маршрут — рисуем только его (и только если у него есть трек);
    ///  - не выбран — рисуем все направления с геометрией.
    /// Направления без трека не рисуются никогда: линии мы не выдумываем.
    public static func routes(variants: [RouteVariant], selectedId: String?) -> [MapRouteOverlay] {
        if let selectedId {
            guard let selected = variants.first(where: { $0.id == selectedId }),
                  selected.hasGeometry else { return [] }
            return [overlay(for: selected, isSelected: true)]
        }
        return variants
            .filter { $0.hasGeometry }
            .map { overlay(for: $0, isSelected: false) }
    }

    /// Остановки выбранного маршрута. Без выбора на карте остановок нет:
    /// 331 маркеров одновременно нечитаемы.
    public static func stops(variant: RouteVariant?) -> [MapStopOverlay] {
        guard let variant, variant.hasGeometry else { return [] }
        return variant.stops.map {
            MapStopOverlay(id: $0.id, lat: $0.lat, lon: $0.lon, title: $0.name)
        }
    }

    /// Остановки по id направления (удобно для View, где есть только id).
    public static func stops(variants: [RouteVariant], selectedId: String?) -> [MapStopOverlay] {
        guard let selectedId,
              let selected = variants.first(where: { $0.id == selectedId }) else { return [] }
        return stops(variant: selected)
    }

    /// Подпись текущего состава объектов. Пока она не изменилась, карту
    /// перерисовывать не нужно: иначе 62 полилинии пересоздавались бы при
    /// каждом обновлении SwiftUI (например, каждую секунду тикера расписаний).
    public static func drawSignature(routes: [MapRouteOverlay], stops: [MapStopOverlay]) -> String {
        let routePart = routes.map { "\($0.id)-\($0.isSelected)-\($0.points.count)" }
            .joined(separator: ",")
        let stopPart = stops.map { String($0.id) }.joined(separator: ",")
        return routePart + "|" + stopPart
    }

    private static func overlay(for variant: RouteVariant, isSelected: Bool) -> MapRouteOverlay {
        MapRouteOverlay(id: variant.id,
                        points: variant.polyline,
                        colorArgb: variant.colorArgb,
                        isSelected: isSelected)
    }
}
