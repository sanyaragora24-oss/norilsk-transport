// LocationManager.swift — обёртка над CLLocationManager, foreground GPS для будильника.
// Соответствует Android StopAlarmService + LocationManager в MapScreen.kt.

import Foundation
import CoreLocation
import UIKit
import Combine

struct Weather {
    let tempC: Double
    let windMs: Double
}

class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var lastLocation: CLLocation?
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    /// Текст последней ошибки геолокации (только для диагностики в UI, без координат).
    @Published var lastError: String?
    @Published var weather: Weather?

    private let manager = CLLocationManager()

    /// Разрешение получено (When In Use или Always).
    var isAuthorized: Bool {
        switch authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: return true
        case .notDetermined, .denied, .restricted: return false
        @unknown default: return false
        }
    }

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.allowsBackgroundLocationUpdates = false
        manager.pausesLocationUpdatesAutomatically = true

        // Текущий статус приходит и через делегат, но не гарантированно сразу:
        // читаем его сами, иначе при повторном запуске (уже с разрешением)
        // обновления могли бы не стартовать.
        authorizationStatus = manager.authorizationStatus
        if isAuthorized {
            manager.startUpdatingLocation()
        } else {
            // Пользователь увидит системный диалог при первом запуске карты.
            // Если доступ уже запрещён, система диалог не покажет — об этом
            // знает UI и предлагает открыть «Настройки».
            manager.requestWhenInUseAuthorization()
        }
    }

    /// Запросить «When In Use». Имеет смысл только для .notDetermined:
    /// после отказа iOS диалог больше не показывает.
    func requestPermission() {
        guard authorizationStatus == .notDetermined else { return }
        manager.requestWhenInUseAuthorization()
    }

    /// Открыть настройки приложения — единственный путь вернуть доступ после отказа.
    func openAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        lastLocation = locations.last
        // TODO: подгрузить погоду с OpenWeather / Yandex Weather по координатам
    }

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        authorizationStatus = status
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            manager.startUpdatingLocation()
        } else {
            // Без разрешения обновления не нужны: экономим батарею и не
            // получаем бесконечных ошибок в консоль.
            manager.stopUpdatingLocation()
            lastLocation = nil
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Сюда приходят в том числе kCLErrorDenied (нет разрешения) и
        // kCLErrorLocationUnknown (GPS ещё не определился) — оба штатные.
        // Падать нельзя: карта работает и без геолокации, просто не показывает
        // точку пользователя.
        lastError = error.localizedDescription
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
