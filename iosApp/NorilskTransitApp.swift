// NorilskTransitApp.swift
// Entry point for iOS app.
// Build: Xcode 16+, iOS 17+ deployment target, YandexMapsMobile 4.45.0-lite (CocoaPods).

import SwiftUI
import YandexMapsMobile
import NorilskTransitCore

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
        //   YMKMapKit.setLocale("ru_RU")
        //   YMKMapKit.setApiKey("<key>")
        //   _ = YMKMapKit.sharedInstance()          ← создаёт синглтон
        // Прежний код использовал YMKMapKitFactory.sharedInstance().apiKey = ...
        // и вообще не создавал инстанс MapKit — карта оставалась неинициализированной.
        //
        // Порядок важен: и локаль, и ключ задаются ДО первого обращения к
        // sharedInstance(). Ключ читается из Info.plist (YANDEX_MAPKIT_API_KEY),
        // куда подставляется значение из gitignored Secrets.xcconfig (локально)
        // или из секрета (релизная сборка).
        //
        // Если ключ не задан или это placeholder — setApiKey не вызывается вовсе:
        // сборка и запуск остаются валидными, карта просто не отрисует тайлы.
        // Сам ключ не логируется и не печатается нигде (см. MapKitKey).
        YMKMapKit.setLocale("ru_RU")
        if let apiKey = MapKitKey.apiKey() {
            YMKMapKit.setApiKey(apiKey)
        }
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
