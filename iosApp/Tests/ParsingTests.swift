// ParsingTests.swift — проверка парсеров JSON.

import XCTest
import NorilskTransitCore

final class ParsingTests: XCTestCase {

    // MARK: - Маршруты

    func testParsesRoutes() throws {
        let json = """
        {
          "routes": [
            {
              "id": "2246:2",
              "busId": 2246,
              "number": "31",
              "slug": "31-kayerkan-hlebozavod",
              "direction": 2,
              "origin": "Кайеркан (ТБК)",
              "destination": "Хлебозавод",
              "colorArgb": 4293467747,
              "stops": [
                {"id": 22649, "name": "ТБК", "lat": 69.3619, "lon": 87.7447},
                {"id": 22650, "name": "Хлебозавод", "lat": 69.3401, "lon": 88.2096}
              ],
              "polyline": [
                {"lat": 69.3619, "lon": 87.7447},
                {"lat": 69.3500, "lon": 87.9000},
                {"lat": 69.3401, "lon": 88.2096}
              ]
            }
          ]
        }
        """
        let routes = try RoutesParser.parse(Data(json.utf8))
        XCTAssertEqual(routes.count, 1)

        let route = routes[0]
        XCTAssertEqual(route.id, "2246:2")
        XCTAssertEqual(route.busId, 2246)
        XCTAssertEqual(route.number, "31")
        XCTAssertEqual(route.direction, 2)
        XCTAssertEqual(route.origin, "Кайеркан (ТБК)")
        XCTAssertEqual(route.destination, "Хлебозавод")
        // Цвет из JSON больше Int32.max — проверяем, что разобрался как UInt32
        XCTAssertEqual(route.colorArgb, 4_293_467_747)
        XCTAssertEqual(route.stops.count, 2)
        XCTAssertEqual(route.stops.first?.name, "ТБК")
        XCTAssertEqual(route.polyline.count, 3)
    }

    func testRejectsBrokenRoutesJSON() {
        let json = "{\"routes\": [{\"id\": \"1:0\"}]}"
        XCTAssertThrowsError(try RoutesParser.parse(Data(json.utf8)))
    }

    func testRejectsRoutesJSONWithoutRoutesKey() {
        let json = "{\"foo\": []}"
        XCTAssertThrowsError(try RoutesParser.parse(Data(json.utf8)))
    }

    // MARK: - Расписания

    func testParsesScheduleWithTimetable() throws {
        let json = """
        {
          "dataDate": "10.09.2025",
          "routes": {
            "3467:0": {
              "number": "31Э",
              "hasSchedule": true,
              "timetable": [
                {"terminal": "Хлебозавод", "weekday": ["06:00", "06:30"], "weekend": ["07:00"]},
                {"terminal": "Кайеркан (ТБК)", "weekday": ["06:40"], "weekend": ["07:40"]}
              ]
            }
          }
        }
        """
        let db = try ScheduleParser.parse(Data(json.utf8))
        XCTAssertEqual(db.dataDate, "10.09.2025")
        XCTAssertEqual(db.routes.count, 1)

        let entry = db.routes["3467:0"]!
        XCTAssertEqual(entry.number, "31Э")
        XCTAssertTrue(entry.hasSchedule)
        XCTAssertEqual(entry.terminals, ["Хлебозавод", "Кайеркан (ТБК)"])
        XCTAssertEqual(entry.timetable[0].weekday, ["06:00", "06:30"])
        XCTAssertEqual(entry.timetable[0].weekend, ["07:00"])
    }

    func testParsesScheduleWithoutTimetable() throws {
        // Старые записи: hasSchedule = false и пустой/отсутствующий timetable
        let json = """
        {
          "routes": {
            "400202:1": {"number": "31Б", "hasSchedule": false, "timetable": []}
          }
        }
        """
        let db = try ScheduleParser.parse(Data(json.utf8))
        let entry = db.routes["400202:1"]!
        XCTAssertFalse(entry.hasSchedule)
        XCTAssertTrue(entry.timetable.isEmpty)
        XCTAssertNil(db.dataDate)
    }

    func testParsesLegacyScheduleEntryWithoutTimetableKey() throws {
        // Защита от старого формата файла, где времена лежали на верхнем уровне
        let json = """
        {
          "routes": {
            "999:0": {
              "number": "99",
              "hasSchedule": true,
              "terminal": "Терминал",
              "weekday": ["08:00"],
              "weekend": ["09:00"]
            }
          }
        }
        """
        let db = try ScheduleParser.parse(Data(json.utf8))
        let entry = db.routes["999:0"]!
        XCTAssertTrue(entry.timetable.isEmpty)
        XCTAssertEqual(entry.terminal, "Терминал")
        XCTAssertEqual(entry.weekday, ["08:00"])
        XCTAssertEqual(entry.weekend, ["09:00"])
    }

    func testRejectsBrokenScheduleJSON() {
        let json = "{\"routes\": {\"1:0\": {\"number\": 5}}}"
        XCTAssertThrowsError(try ScheduleParser.parse(Data(json.utf8)))
    }
}
