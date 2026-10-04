// ScheduleLogicTests.swift — проверка работы со временем расписания.

import XCTest
import NorilskTransitCore

final class ScheduleLogicTests: XCTestCase {

    func testParseTime() {
        XCTAssertEqual(ScheduleLogic.parseTime("00:00"), 0)
        XCTAssertEqual(ScheduleLogic.parseTime("07:20"), 440)
        XCTAssertEqual(ScheduleLogic.parseTime("23:59"), 1439)
    }

    func testParseTimeAcceptsSingleDigitHour() {
        // Формат в данных МУП всегда HH:mm, но час из одной цифры принимаем:
        // это то же время, просто записанное короче.
        XCTAssertEqual(ScheduleLogic.parseTime("7:20"), 440)
    }

    func testParseTimeRejectsGarbage() {
        XCTAssertNil(ScheduleLogic.parseTime("0720"))   // нет разделителя
        XCTAssertNil(ScheduleLogic.parseTime("25:00"))  // час вне диапазона
        XCTAssertNil(ScheduleLogic.parseTime("12:60"))  // минуты вне диапазона
        XCTAssertNil(ScheduleLogic.parseTime(""))
        XCTAssertNil(ScheduleLogic.parseTime("нет данных"))
    }

    func testFormatMinutesRoundTrip() {
        XCTAssertEqual(ScheduleLogic.formatMinutes(0), "00:00")
        XCTAssertEqual(ScheduleLogic.formatMinutes(440), "07:20")
        XCTAssertEqual(ScheduleLogic.formatMinutes(1439), "23:59")
        XCTAssertEqual(ScheduleLogic.formatMinutes(ScheduleLogic.parseTime("06:05")!), "06:05")
    }

    func testWeekendDetection() {
        // Calendar.component(.weekday): 1 = воскресенье … 7 = суббота
        XCTAssertTrue(ScheduleLogic.isWeekend(weekdayIndex: 1))
        XCTAssertTrue(ScheduleLogic.isWeekend(weekdayIndex: 7))
        for day in 2...6 {
            XCTAssertFalse(ScheduleLogic.isWeekend(weekdayIndex: day))
        }
    }

    func testMinutesOfDate() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let date = calendar.date(from: DateComponents(year: 2025, month: 3, day: 10, hour: 14, minute: 35))!
        XCTAssertEqual(ScheduleLogic.minutes(of: date, calendar: calendar), 14 * 60 + 35)
    }

    func testNormalizedTimesSortsAndDeduplicates() {
        let times = ["10:00", "07:20", "07:20", "мусор", "06:05"]
        XCTAssertEqual(ScheduleLogic.normalizedTimes(times), [365, 440, 600])
    }

    func testNextDeparturesTakesUpcomingOnly() {
        let times = ["06:00", "07:00", "08:00", "09:00"]
        let next = ScheduleLogic.nextDepartures(times: times, nowMinutes: 7 * 60 + 1, limit: 3)
        XCTAssertEqual(next, [480, 540])
    }

    func testNextDeparturesIncludesCurrentMinute() {
        // Если отправление ровно сейчас — оно ещё актуально
        let next = ScheduleLogic.nextDepartures(times: ["07:00", "08:00"], nowMinutes: 420, limit: 5)
        XCTAssertEqual(next, [420, 480])
    }

    func testNextDeparturesDoesNotWrapToNextDay() {
        // Важно: мы не переносим "завтрашние" рейсы, данных для этого нет
        let next = ScheduleLogic.nextDepartures(times: ["07:00", "08:00"], nowMinutes: 1_380, limit: 5)
        XCTAssertTrue(next.isEmpty)
    }

    func testNextDeparturesRespectsLimit() {
        let times = ["06:00", "07:00", "08:00", "09:00", "10:00"]
        let next = ScheduleLogic.nextDepartures(times: times, nowMinutes: 0, limit: 2)
        XCTAssertEqual(next, [360, 420])
    }

    func testCountdownText() {
        XCTAssertEqual(ScheduleLogic.countdownText(departure: 450, nowMinutes: 450), "сейчас")
        XCTAssertEqual(ScheduleLogic.countdownText(departure: 462, nowMinutes: 450), "через 12 мин")
        XCTAssertEqual(ScheduleLogic.countdownText(departure: 510, nowMinutes: 450), "через 1 ч")
        XCTAssertEqual(ScheduleLogic.countdownText(departure: 515, nowMinutes: 450), "через 1 ч 05 мин")
    }
}
