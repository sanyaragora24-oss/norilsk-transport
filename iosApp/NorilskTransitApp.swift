// NorilskTransitApp.swift
// Entry point for iOS app.
// Build requirements: Xcode 16+, iOS 17+ deployment target, YandexMapsMobile via CocoaPods.

import SwiftUI
import YandexMapsMobile

@main
struct NorilskTransitApp: App {
    @StateObject private var routesStore = RoutesStore()
    @StateObject private var locationManager = LocationManager()
    @StateObject private var favoritesStore = FavoritesStore()

    init() {
        // Initialize Yandex MapKit with API key from Info.plist
        if let apiKey = Bundle.main.object(forInfoDictionaryKey: "YANDEX_MAPKIT_API_KEY") as? String {
            YMKMapKitFactory.sharedInstance().apiKey = apiKey
        }
    }

    var body: some Scene {
        WindowGroup {
            MapScreen()
                .environmentObject(routesStore)
                .environmentObject(locationManager)
                .environmentObject(favoritesStore)
                .preferredColorScheme(.dark)
        }
    }
}
