// StopDetailView.swift — экран остановки: какие маршруты через неё идут.

import SwiftUI
import CoreLocation
import NorilskTransitCore

struct StopDetailView: View {
    @EnvironmentObject private var store: TransitStore
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var locationManager: LocationManager

    let stop: StopInfo
    private let onShowOnMap: ((StopInfo) -> Void)?

    init(stop: StopInfo, onShowOnMap: ((StopInfo) -> Void)? = nil) {
        self.stop = stop
        self.onShowOnMap = onShowOnMap
    }

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 6) {
                    Text(stop.name).font(.title3).bold()
                    Text(coordinatesText)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                    if let distanceText {
                        Label(distanceText, systemImage: "location.fill")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 4)
            }

            Section {
                ForEach(routes) { variant in
                    NavigationLink(value: variant) {
                        RouteVariantRow(variant: variant, isFavorite: favorites.routes.contains(variant.id))
                    }
                }
            } header: {
                Text("Маршруты через остановку · \(routes.count)")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(stop.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                if let onShowOnMap {
                    Button("На карту") { onShowOnMap(stop) }
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    favorites.toggleStop(stop.id)
                } label: {
                    Image(systemName: favorites.stops.contains(stop.id) ? "star.fill" : "star")
                }
            }
        }
        .navigationDestination(for: RouteVariant.self) { RouteDetailView(variant: $0) }
    }

    private var routes: [RouteVariant] {
        store.index?.routesThrough(stopId: stop.id) ?? []
    }

    private var coordinatesText: String {
        String(format: "%.5f, %.5f", stop.lat, stop.lon)
    }

    private var distanceText: String? {
        guard let location = locationManager.lastLocation else { return nil }
        let meters = Geo.distanceMeters(fromLat: location.coordinate.latitude,
                                        fromLon: location.coordinate.longitude,
                                        toLat: stop.lat,
                                        toLon: stop.lon)
        if meters < 1000 {
            return "\(Int(meters)) м от вас"
        }
        return String(format: "%.1f км от вас", meters / 1000)
    }
}
