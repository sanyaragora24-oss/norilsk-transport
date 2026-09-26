// RoutesStore.swift — загрузка маршрутов из bundled JSON, аналог Android-версии.
// TODO: заменить на KMP shared модуль после рефакторинга.

import Foundation
import Combine

struct Route: Identifiable, Hashable {
    let id: String           // "2246:2"
    let busId: Int
    let number: String       // "31"
    let direction: Int
    let origin: String
    let destination: String
    let colorArgb: UInt32
    let stops: [Stop]
    let polyline: [LatLon]
}

struct Stop: Identifiable, Hashable {
    let id: Int
    let name: String
    let lat: Double
    let lon: Double
}

struct LatLon: Hashable, Codable {
    let lat: Double
    let lon: Double
}

extension Route {
    init(json: [String: Any]) {
        self.id = json["id"] as? String ?? ""
        self.busId = json["busId"] as? Int ?? 0
        self.number = json["number"] as? String ?? ""
        self.direction = json["direction"] as? Int ?? 0
        self.origin = json["origin"] as? String ?? ""
        self.destination = json["destination"] as? String ?? ""
        self.colorArgb = json["colorArgb"] as? UInt32 ?? 0xFFE0E0E0
        self.stops = (json["stops"] as? [[String: Any]] ?? []).map { Stop(json: $0) }
        // polyline: [{lat, lon}, ...]
        let polyArr = json["polyline"] as? [[String: Any]] ?? []
        self.polyline = polyArr.compactMap { LatLon(lat: $0["lat"] as? Double ?? 0, lon: $0["lon"] as? Double ?? 0) }
    }
}

extension Stop {
    init(json: [String: Any]) {
        self.id = json["id"] as? Int ?? 0
        self.name = json["name"] as? String ?? ""
        self.lat = json["lat"] as? Double ?? 0
        self.lon = json["lon"] as? Double ?? 0
    }
}

extension UIColor {
    convenience init(_ argb: UInt32) {
        let r = CGFloat((argb >> 16) & 0xFF) / 255
        let g = CGFloat((argb >> 8) & 0xFF) / 255
        let b = CGFloat(argb & 0xFF) / 255
        self.init(red: r, green: g, blue: b, alpha: 1)
    }
}

class RoutesStore: ObservableObject {
    @Published var routes: [Route] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    init() {
        loadRoutes()
    }

    func loadRoutes() {
        isLoading = true
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            do {
                guard let url = Bundle.main.url(forResource: "norilsk_routes", withExtension: "json") else {
                    throw NSError(domain: "RoutesStore", code: 1, userInfo: [NSLocalizedDescriptionKey: "norilsk_routes.json not found in bundle"])
                }
                let data = try Data(contentsOf: url)
                let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
                let routesArr = json["routes"] as? [[String: Any]] ?? []
                let routes = routesArr.map { Route(json: $0) }
                DispatchQueue.main.async {
                    self?.routes = routes
                    self?.isLoading = false
                }
            } catch {
                DispatchQueue.main.async {
                    self?.errorMessage = error.localizedDescription
                    self?.isLoading = false
                }
            }
        }
    }
}
