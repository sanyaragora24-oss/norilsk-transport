// Parsing.swift — разбор norilsk_routes.json и norilsk_schedule.json.
//
// Парсеры чистые: принимают Data, возвращают модели или бросают ошибку.
// Никакого доступа к Bundle — этим занимается слой приложения,
// поэтому парсеры полностью покрываются unit-тестами.

import Foundation

public enum RoutesParser {
    public struct RoutesFile: Decodable {
        public let routes: [Route]
    }

    public static func parse(_ data: Data) throws -> [Route] {
        let decoder = JSONDecoder()
        let file = try decoder.decode(RoutesFile.self, from: data)
        return file.routes
    }
}

public enum ScheduleParser {
    public static func parse(_ data: Data) throws -> ScheduleDatabase {
        let decoder = JSONDecoder()
        return try decoder.decode(ScheduleDatabase.self, from: data)
    }
}

public enum TransitDataError: LocalizedError {
    case fileNotFound(name: String)
    case decodingFailed(name: String, underlying: Error)

    public var errorDescription: String? {
        switch self {
        case .fileNotFound(let name):
            return "Файл \(name) не найден в бандле"
        case .decodingFailed(let name, let underlying):
            return "Не удалось разобрать \(name): \(underlying.localizedDescription)"
        }
    }
}

/// Загрузка данных из бандла. Используется приложением; в тестах не вызывается.
public enum BundleDataLoader {
    public static func loadData(resourceName: String, bundle: Bundle) throws -> Data {
        // Обычно ресурсы лежат в корне бандла.
        if let url = bundle.url(forResource: resourceName, withExtension: "json") {
            return try Data(contentsOf: url)
        }
        // XcodeGen может скопировать Resources как folder reference — тогда
        // файлы оказываются в подкаталоге Resources/.
        if let url = bundle.url(forResource: "Resources/\(resourceName)", withExtension: "json") {
            return try Data(contentsOf: url)
        }
        let candidates = bundle.urls(forResourcesWithExtension: "json", subdirectory: nil) ?? []
        if let match = candidates.first(where: { $0.lastPathComponent == "\(resourceName).json" }) {
            return try Data(contentsOf: match)
        }
        throw TransitDataError.fileNotFound(name: "\(resourceName).json")
    }
}
