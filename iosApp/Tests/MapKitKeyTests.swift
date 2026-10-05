// MapKitKeyTests.swift — логика состояния ключа Яндекс.Карт.
//
// Тесты идут в CI, где ключа заведомо нет, поэтому проверяется «чистая»
// функция MapKitKey.state(forValue:), а не чтение бандла: в тестах Bundle.main —
// это раннер тестов, а не .app.

import XCTest
import NorilskTransitCore

final class MapKitKeyTests: XCTestCase {

    func testMissingWhenValueIsNil() {
        XCTAssertEqual(MapKitKey.state(forValue: nil), .missing)
    }

    func testMissingWhenValueIsEmptyOrWhitespace() {
        XCTAssertEqual(MapKitKey.state(forValue: ""), .missing)
        XCTAssertEqual(MapKitKey.state(forValue: "   "), .missing)
        XCTAssertEqual(MapKitKey.state(forValue: "\n"), .missing)
    }

    func testMissingWhenBuildSettingWasNotSubstituted() {
        // Secrets.xcconfig не создан или переменная не задана — в plist остаётся
        // буквальная подстановка. Это «ключа нет», а не валидный ключ.
        XCTAssertEqual(MapKitKey.state(forValue: "$(YANDEX_MAPKIT_API_KEY)"), .missing)
    }

    func testMissingForKnownPlaceholders() {
        for placeholder in MapKitKey.placeholderValues {
            XCTAssertEqual(MapKitKey.state(forValue: placeholder), .missing,
                           "placeholder \(placeholder) должен считаться отсутствующим ключом")
        }
    }

    func testConfiguredForRealLookingKey() {
        XCTAssertEqual(MapKitKey.state(forValue: "01234567-89ab-cdef-0123-456789abcdef"), .configured)
        // Пробелы по краям не делают ключ невалидным
        XCTAssertEqual(MapKitKey.state(forValue: "  abcdef01  "), .configured)
    }

    func testTitlesNeverContainKeyValue() {
        // В интерфейс уходит только состояние. Регрессионная защита: если кто-то
        // добавит в title сам ключ, тест это поймает.
        let key = "01234567-89ab-cdef-0123-456789abcdef"
        XCTAssertFalse(MapKitKey.state(forValue: key).title.contains(key))
        XCTAssertEqual(MapKitKey.state(forValue: key).title, "задан")
        XCTAssertEqual(MapKitKey.state(forValue: nil).title, "не задан")
    }

    func testInfoPlistKeyName() {
        XCTAssertEqual(MapKitKey.infoPlistKey, "YANDEX_MAPKIT_API_KEY")
    }
}
