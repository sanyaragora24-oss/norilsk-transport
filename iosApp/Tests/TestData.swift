// TestData.swift — загрузка JSON для тестов.

import Foundation
import NorilskTransitCore

private final class BundleToken {}

enum TestData {
    /// JSON-ресурсы тестового бандла. Если Xcode не скопировал ресурсы
    /// в .xctest, берём файл из репозитория по пути исходника — в CI
    /// исходники лежат там же, где и собирались.
    static func data(resourceName: String) throws -> Data {
        let bundle = Bundle(for: BundleToken.self)
        if let url = bundle.url(forResource: resourceName, withExtension: "json") {
            return try Data(contentsOf: url)
        }
        let fallback = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../Resources/\(resourceName).json")
            .standardizedFileURL
        return try Data(contentsOf: fallback)
    }
}
