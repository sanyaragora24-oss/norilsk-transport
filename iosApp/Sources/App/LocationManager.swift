// LocationManager.swift — обёртка над CLLocationManager, foreground GPS для будильника.
// Соответствует Android StopAlarmService + LocationManager в MapScreen.kt.

import Foundation
import CoreLocation
import Combine

struct Weather {
    let tempC: Double
    let windMs: Double
}

class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var lastLocation: CLLocation?
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published var weather: Weather?

    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.allowsBackgroundLocationUpdates = false
        manager.pausesLocationUpdatesAutomatically = true
        // Запросить разрешения — пользователь увидит диалог при первом запуске карты
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        lastLocation = locations.last
        // TODO: подгрузить погоду с OpenWeather / Yandex Weather по координатам
    }

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        authorizationStatus = status
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            manager.startUpdatingLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // ignored
    }
}

class FavoritesStore: ObservableObject {
    @Published var stops: [Int] = []  // IDs избранных остановок
    @Published var routes: [String] = []  // IDs избранных маршрутов

    private let stopsKey = "favorite_stops"
    private let routesKey = "favorite_routes"

    init() {
        let defaults = UserDefaults.standard
        stops = defaults.array(forKey: stopsKey) as? [Int] ?? []
        routes = defaults.array(forKey: routesKey) as? [String] ?? []
    }

    func toggleStop(_ id: Int) {
        if stops.contains(id) { stops.removeAll { $0 == id } } else { stops.append(id) }
        UserDefaults.standard.set(stops, forKey: stopsKey)
    }

    func toggleRoute(_ id: String) {
        if routes.contains(id) { routes.removeAll { $0 == id } } else { routes.append(id) }
        UserDefaults.standard.set(routes, forKey: routesKey)
    }
}
