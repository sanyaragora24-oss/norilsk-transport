// TransitIndexTests.swift — проверка объединения маршрутов и расписаний на реальных данных.
//
// Тесты читают те же JSON-файлы, что и приложение (они попадают в тестовый бандл),
// поэтому расхождение данных с ожиданиями здесь видно сразу.

import XCTest
import NorilskTransitCore

final class TransitIndexTests: XCTestCase {

    private var index: TransitIndex!

    override func setUpWithError() throws {
        let routes = try RoutesParser.parse(try TestData.data(resourceName: "norilsk_routes"))
        let schedule = try ScheduleParser.parse(try TestData.data(resourceName: "norilsk_schedule"))
        index = TransitIndex(routes: routes, schedule: schedule)
    }

    // MARK: - Общая структура

    func testVariantCounts() {
        // 54 направления с геометрией + 8 записей только с расписанием
        XCTAssertEqual(index.variants.count, 62)
        XCTAssertEqual(index.variants.filter { $0.hasGeometry }.count, 54)
        XCTAssertEqual(index.variants.filter { !$0.hasGeometry }.count, 8)
    }

    func testScheduleOnlyIdsAreExactly31Family() {
        let scheduleOnly = index.variants.filter { !$0.hasGeometry }.map { $0.id }.sorted()
        XCTAssertEqual(
            scheduleOnly,
            ["2246:0", "2246:1", "3467:0", "3467:1", "3467:2", "3467:3", "3467:4", "3467:5"]
        )
    }

    func testEveryGeometryVariantHasLineAndStops() {
        for variant in index.variants where variant.hasGeometry {
            XCTAssertGreaterThanOrEqual(variant.polyline.count, 2, "нет линии у \(variant.id)")
            XCTAssertFalse(variant.stops.isEmpty, "нет остановок у \(variant.id)")
            XCTAssertNotNil(variant.origin)
            XCTAssertNotNil(variant.destination)
        }
    }

    func testEveryGeometryVariantIsSortedByNumber() {
        let sorted = index.variants.map { TransitIndex.numberSortKey($0.number) }
        let expected = sorted.sorted { lhs, rhs in
            lhs.digits != rhs.digits ? lhs.digits < rhs.digits : lhs.letters < rhs.letters
        }
        XCTAssertEqual(sorted.map { "\($0.digits)\($0.letters)" }, expected.map { "\($0.digits)\($0.letters)" })
    }

    // MARK: - 31 / 31Э

    func test31EHasSixDirectionsWithoutGeometry() {
        let variants = index.variants(number: "31Э")
        XCTAssertEqual(variants.count, 6)

        // 31Э: геометрия МУП не опубликована — линии нет, но расписание есть
        XCTAssertTrue(variants.allSatisfy { !$0.hasGeometry })
        XCTAssertTrue(variants.allSatisfy { $0.stops.isEmpty })
        XCTAssertTrue(variants.allSatisfy { $0.polyline.isEmpty })
        XCTAssertTrue(variants.allSatisfy { $0.hasSchedule })

        let terminals = Set(variants.map { $0.schedule?.terminals ?? [] })
        XCTAssertEqual(terminals.count, 1)
        XCTAssertEqual(
            terminals.first?.sorted(),
            ["Кайеркан (ТБК)", "Норильск (АДЦ)", "Пождепо", "Хлебозавод"]
        )
    }

    func test31EIsNotMergedWith31() {
        // Явно проверяем, что 31 и 31Э остаются разными маршрутами
        let thirtyOne = Set(index.variants(number: "31").map { $0.id })
        let thirtyOneE = Set(index.variants(number: "31Э").map { $0.id })
        XCTAssertFalse(thirtyOne.isEmpty)
        XCTAssertTrue(thirtyOne.isDisjoint(with: thirtyOneE))
        XCTAssertEqual(index.variants(number: "31").count, 6)  // 4 с геометрией + 2 старых
    }

    func testLegacy31DirectionsAreScheduleOnly() {
        XCTAssertFalse(index.variant(id: "2246:0")?.hasGeometry ?? true)
        XCTAssertFalse(index.variant(id: "2246:1")?.hasGeometry ?? true)
        XCTAssertTrue(index.variant(id: "2246:2")?.hasGeometry ?? false)
        XCTAssertTrue(index.variant(id: "2246:3")?.hasGeometry ?? false)
        XCTAssertTrue(index.variant(id: "2246:4")?.hasGeometry ?? false)
        XCTAssertTrue(index.variant(id: "2246:5")?.hasGeometry ?? false)
    }

    func testVariantsWithoutScheduleHaveNoTimetable() {
        let noSchedule = index.variants.filter { !$0.hasSchedule }
        XCTAssertFalse(noSchedule.isEmpty)
        XCTAssertTrue(noSchedule.allSatisfy { $0.schedule?.timetable.isEmpty ?? true })
    }

    // MARK: - Остановки

    func testStopIndexIsBuiltFromGeometryRoutes() {
        XCTAssertEqual(index.stops.count, 331)
        XCTAssertTrue(index.stops.allSatisfy { !$0.routeIds.isEmpty })
    }

    func testRoutesThroughStopAreConsistent() {
        let stop = index.stops[0]
        let routes = index.routesThrough(stopId: stop.id)
        XCTAssertEqual(Set(routes.map { $0.id }), Set(stop.routeIds))
        XCTAssertTrue(routes.allSatisfy { $0.stops.contains { $0.id == stop.id } })
    }

    func testGeoDistanceForOneDegreeOfLatitude() {
        // Сферическая формула: один градус широты = 2πR/360 ≈ 111 195 м
        let distance = Geo.distanceMeters(fromLat: 69.0, fromLon: 88.0, toLat: 70.0, toLon: 88.0)
        XCTAssertEqual(distance, 111_195, accuracy: 50)
    }

    func testGeoDistanceIsSymmetricAndZeroForSamePoint() {
        XCTAssertEqual(Geo.distanceMeters(fromLat: 69.34, fromLon: 88.21, toLat: 69.34, toLon: 88.21), 0, accuracy: 0.001)
        let direct = Geo.distanceMeters(fromLat: 69.34, fromLon: 88.21, toLat: 69.5, toLon: 88.0)
        let reverse = Geo.distanceMeters(fromLat: 69.5, fromLon: 88.0, toLat: 69.34, toLon: 88.21)
        XCTAssertEqual(direct, reverse, accuracy: 0.001)
    }

    func testNearestStopsRespectsLimit() {
        XCTAssertEqual(index.nearestStops(latitude: 69.34, longitude: 88.21, limit: 3).count, 3)
        XCTAssertLessThanOrEqual(index.nearestStops(latitude: 69.34, longitude: 88.21, limit: 10_000).count,
                                 index.stops.count)
    }

    func testNearestStopsAreSortedByDistance() {
        let nearest = index.nearestStops(latitude: 69.34, longitude: 88.21, limit: 5)
        XCTAssertEqual(nearest.count, 5)
        let distances = nearest.map { $0.distance }
        XCTAssertEqual(distances, distances.sorted())
        XCTAssertLessThan(distances[0], 300)
        XCTAssertEqual(nearest[0].stop.id, 22345)
    }

    // MARK: - Поиск

    func testSearchRoutesByNumber() {
        let found = index.searchRoutes("31")
        let numbers = Set(found.map { $0.number })
        XCTAssertTrue(numbers.contains("31"))
        XCTAssertTrue(numbers.contains("31Э"))
        XCTAssertTrue(numbers.contains("31Б"))
    }

    func testSearchRoutesIsCaseAndYoInsensitive() {
        let direct = index.searchRoutes("хлебозавод").count
        let otherCase = index.searchRoutes("ХЛЕБОЗАВОД").count
        XCTAssertGreaterThan(direct, 0)
        XCTAssertEqual(direct, otherCase)
    }

    func testSearchStopsByName() {
        let found = index.searchStops("универ")
        XCTAssertFalse(found.isEmpty)
        XCTAssertTrue(found.allSatisfy { $0.name.localizedCaseInsensitiveContains("универ") })
    }

    func testEmptyQueryReturnsEverything() {
        XCTAssertEqual(index.searchRoutes("").count, index.variants.count)
        XCTAssertEqual(index.searchStops("").count, index.stops.count)
    }

    // MARK: - Утилиты

    func testDirectionFromId() {
        XCTAssertEqual(TransitIndex.directionFromId("2246:2"), 2)
        XCTAssertEqual(TransitIndex.directionFromId("3467:0"), 0)
        XCTAssertNil(TransitIndex.directionFromId("без-двоеточия"))
    }

    func testNumberSortKeySortsNaturally() {
        let numbers = ["31", "4", "1А", "22", "22И", "2"]
        let sorted = numbers.sorted { lhs, rhs in
            let lk = TransitIndex.numberSortKey(lhs)
            let rk = TransitIndex.numberSortKey(rhs)
            return lk.digits != rk.digits ? lk.digits < rk.digits : lk.letters < rk.letters
        }
        XCTAssertEqual(sorted, ["1А", "2", "4", "22", "22И", "31"])
    }

    func testFirstNumbersAreInNaturalOrder() {
        XCTAssertEqual(Array(index.numbers.prefix(3)), ["1А", "1Б", "2"])
    }
}
