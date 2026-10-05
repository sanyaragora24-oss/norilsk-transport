// MapOverlaysTests.swift — что именно приложение рисует на карте.
//
// Тесты идут на настоящих production-данных (62 направления), поэтому покрывают
// то, что визуально в CI не проверить: сколько линий попадает на карту без
// выбора и при выборе, какие остановки рисуются, стабильна ли подпись
// перерисовки. Проверка самих тайлов и жестов требует живого ключа и устройства.

import XCTest
import NorilskTransitCore

final class MapOverlaysTests: XCTestCase {

    private var index: TransitIndex!

    override func setUpWithError() throws {
        let routes = try RoutesParser.parse(try TestData.data(resourceName: "norilsk_routes"))
        let schedule = try ScheduleParser.parse(try TestData.data(resourceName: "norilsk_schedule"))
        index = TransitIndex(routes: routes, schedule: schedule)
    }

    // MARK: - Линии

    func testAllGeometryVariantsAreDrawnWithoutSelection() {
        let overlays = MapOverlays.routes(variants: index.variants, selectedId: nil)
        XCTAssertEqual(overlays.count, 62)
        XCTAssertTrue(overlays.allSatisfy { !$0.isSelected })
        XCTAssertTrue(overlays.allSatisfy { $0.points.count >= 2 })
        // Каждый overlay соответствует реальному направлению с треком
        XCTAssertEqual(Set(overlays.map(\.id)),
                       Set(index.variants.filter { $0.hasGeometry }.map(\.id)))
    }

    func testOnlySelectedRouteIsDrawn() {
        let overlays = MapOverlays.routes(variants: index.variants, selectedId: "2246:2")
        XCTAssertEqual(overlays.count, 1)
        XCTAssertEqual(overlays.first?.id, "2246:2")
        XCTAssertEqual(overlays.first?.isSelected, true)
        XCTAssertEqual(overlays.first?.points.count, 575)
    }

    func testBoth31And31EAreDrawable() {
        // 31 и 31Э остаются разными маршрутами — и те, и другие рисуются.
        for id in ["2246:0", "2246:1", "2246:2", "2246:3", "2246:4", "2246:5"] {
            XCTAssertEqual(MapOverlays.routes(variants: index.variants, selectedId: id).count, 1, "31: \(id)")
        }
        for id in ["3467:0", "3467:1", "3467:2", "3467:3", "3467:4", "3467:5"] {
            XCTAssertEqual(MapOverlays.routes(variants: index.variants, selectedId: id).count, 1, "31Э: \(id)")
        }
    }

    func testUnknownSelectionDrawsNothing() {
        XCTAssertTrue(MapOverlays.routes(variants: index.variants, selectedId: "такого-нет").isEmpty)
        XCTAssertTrue(MapOverlays.stops(variants: index.variants, selectedId: "такого-нет").isEmpty)
        XCTAssertTrue(MapOverlays.routes(variants: [], selectedId: nil).isEmpty)
        XCTAssertTrue(MapOverlays.stops(variant: nil).isEmpty)
    }

    // MARK: - Остановки

    func testStopsAreDrawnOnlyForSelectedRoute() {
        XCTAssertTrue(MapOverlays.stops(variants: index.variants, selectedId: nil).isEmpty)

        guard let variant = index.variant(id: "3467:0") else {
            XCTFail("в production-данных нет направления 3467:0")
            return
        }
        let stops = MapOverlays.stops(variants: index.variants, selectedId: "3467:0")
        XCTAssertEqual(stops.count, variant.stops.count)
        XCTAssertEqual(stops.count, 28)
        XCTAssertEqual(Set(stops.map(\.id)), Set(variant.stops.map(\.id)))
        // Названия и координаты не подменяются
        XCTAssertEqual(stops.first?.title, variant.stops.first?.name)
        XCTAssertEqual(stops.first?.lat, variant.stops.first?.lat)
    }

    func testStopCoordinatesAreInsideNorilsk() {
        let stops = MapOverlays.stops(variants: index.variants, selectedId: "2246:0")
        XCTAssertFalse(stops.isEmpty)
        for stop in stops {
            XCTAssertGreaterThan(stop.lat, 68.5, "широта вне Норильска: \(stop.title)")
            XCTAssertLessThan(stop.lat, 70.0, "широта вне Норильска: \(stop.title)")
            XCTAssertGreaterThan(stop.lon, 86.0, "долгота вне Норильска: \(stop.title)")
            XCTAssertLessThan(stop.lon, 90.0, "долгота вне Норильска: \(stop.title)")
        }
    }

    // MARK: - Перерисовка

    func testDrawSignatureIsStableWithoutChanges() {
        let stops = MapOverlays.stops(variants: index.variants, selectedId: nil)
        let first = MapOverlays.drawSignature(
            routes: MapOverlays.routes(variants: index.variants, selectedId: nil), stops: stops)
        let second = MapOverlays.drawSignature(
            routes: MapOverlays.routes(variants: index.variants, selectedId: nil), stops: stops)
        // Тик таймера расписаний вызывает updateUIView каждую секунду —
        // подпись обязана остаться прежней, иначе карта перерисовывается зря.
        XCTAssertEqual(first, second)
    }

    func testDrawSignatureChangesOnSelection() {
        let all = MapOverlays.drawSignature(
            routes: MapOverlays.routes(variants: index.variants, selectedId: nil), stops: [])
        let selected = MapOverlays.drawSignature(
            routes: MapOverlays.routes(variants: index.variants, selectedId: "2246:2"), stops: [])
        XCTAssertNotEqual(all, selected)
    }
}
