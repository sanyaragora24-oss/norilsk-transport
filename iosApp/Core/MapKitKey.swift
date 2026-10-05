// MapKitKey.swift — состояние API-ключа Яндекс.Карт.
//
// Правила, ради которых этот файл существует:
//  1. Ключ никогда не лежит в исходниках: он приходит в Info.plist подстановкой
//     $(YANDEX_MAPKIT_API_KEY) из gitignored iosApp/Secrets.xcconfig (локально)
//     либо из секрета на этапе релизной сборки.
//  2. Ключ никогда не печатается: ни в логи, ни в notice CI, ни в отчёты.
//     Всё, что видно снаружи — состояние «задан / не задан».
//  3. Без ключа сборка и запуск остаются валидными: MapKit не инициализируется
//     с пустым ключом, тайлы просто не грузятся, приложение не падает.
//
// Логика намеренно «чистая» (без обращения к бандлу), чтобы её можно было
// проверить unit-тестами: в тестах Bundle.main — это раннер тестов, а не .app.

import Foundation

public enum MapKitKeyState: String, Equatable, Sendable, CaseIterable {
    case configured
    case missing

    /// Текст для интерфейса: в UI показываем только состояние, не значение.
    public var title: String {
        switch self {
        case .configured: return "задан"
        case .missing: return "не задан"
        }
    }

    public var isConfigured: Bool { self == .configured }
}

public enum MapKitKey {

    /// Ключ в Info.plist.
    public static let infoPlistKey = "YANDEX_MAPKIT_API_KEY"

    /// Значения, которые означают «ключа нет»:
    ///  - пустая строка (Secrets.xcconfig без значения),
    ///  - незакрытая подстановка build setting'а `$(YANDEX_MAPKIT_API_KEY)`,
    ///  - известные placeholder'ы шаблона и CI.
    public static let placeholderValues: Set<String> = [
        "placeholder-update-before-release",
        "MAPKIT_KEY_NOT_SET",
        "YOUR_API_KEY",
    ]

    /// Состояние ключа по значению из Info.plist. Чистая функция — тестируется напрямую.
    public static func state(forValue value: String?) -> MapKitKeyState {
        guard let value else { return .missing }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return .missing }
        // Подстановка не сработала (переменная окружения/build setting не задан).
        if trimmed.hasPrefix("$(") { return .missing }
        if placeholderValues.contains(trimmed) { return .missing }
        return .configured
    }

    /// Состояние ключа в работающем приложении.
    public static var currentState: MapKitKeyState {
        state(forValue: Bundle.main.object(forInfoDictionaryKey: infoPlistKey) as? String)
    }

    /// Сам ключ. Единственное место, где он читается; вызывается ровно один раз
    /// при старте приложения и не попадает ни в логи, ни в отчёты.
    /// Возвращает nil, если ключ не задан или является placeholder'ом.
    public static func apiKey() -> String? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: infoPlistKey) as? String,
              state(forValue: raw).isConfigured else { return nil }
        return raw.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
