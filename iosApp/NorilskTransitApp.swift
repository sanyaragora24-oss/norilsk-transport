// NorilskTransitApp.swift
// Entry point for iOS app.
// Build: Xcode 16+, iOS 17+ deployment target, YandexMapsMobile 4.45.0-lite (CocoaPods).

import SwiftUI
import YandexMapsMobile

@main
struct NorilskTransitApp: App {
    @StateObject private var store = TransitStore()
    @StateObject private var locationManager = LocationManager()
    @StateObject private var favoritesStore = FavoritesStore()

    init() {
        configureMapKit()
    }

    private func configureMapKit() {
        // MapKit 4.x инициализируется так:
        //   YMKMapKit.setApiKey("<key>")
        //   YMKMapKit.sharedInstance()
        // Прежний код использовал YMKMapKitFactory.sharedInstance().apiKey = ...
        // и вообще не создавал инстанс MapKit — карта оставалась неинициализированной.
        //
        // Ключ читается из Info.plist (YANDEX_MAPKIT_API_KEY); значение подставляется
        // на этапе сборки из переменной окружения/секрета. Если ключ не задан
        // (в CI подставляется placeholder), setApiKey не вызывается — сборка и запуск
        // остаются валидными, карта просто не отрисует тайлы.
        if let apiKey = Bundle.main.object(forInfoDictionaryKey: "YANDEX_MAPKIT_API_KEY") as? String,
           !apiKey.isEmpty,
           !apiKey.hasPrefix("$(") {
            YMKMapKit.setApiKey(apiKey)
        }

        YMKMapKit.setLocale("ru_RU")
        _ = YMKMapKit.sharedInstance()
    }

    var body: some Scene {
        WindowGroup {
            MapScreen()
                .environmentObject(store)
                .environmentObject(locationManager)
                .environmentObject(favoritesStore)
                .preferredColorScheme(.dark)
        }
    }
}
