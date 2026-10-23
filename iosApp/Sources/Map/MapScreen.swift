// MapScreen.swift — карта Яндекс с маршрутами автобусов Норильска.
// Структура соответствует Android-версии (MapScreen.kt).

import SwiftUI
import YandexMapsMobile
import CoreLocation

struct MapScreen: View {
    @EnvironmentObject var routesStore: RoutesStore
    @EnvironmentObject var locationManager: LocationManager

    @State private var selectedRoute: Route?
    @State private var showRouteList = false
    @State private var showFavorites = false
    @State private var showMenu = false

    var body: some View {
        ZStack(alignment: .top) {
            YandexMapView(routes: routesStore.routes,
                          selectedRoute: selectedRoute,
                          userLocation: locationManager.lastLocation)
                .ignoresSafeArea()

            MapTopBar(
                weather: locationManager.weather,
                onMenuTap: { showMenu = true },
                onFavoritesTap: { showFavorites = true }
            )

            VStack {
                Spacer()
                if showRouteList {
                    RouteListSheet(
                        routes: routesStore.routes,
                        onSelect: { route in
                            selectedRoute = route
                            showRouteList = false
                        },
                        onClose: { showRouteList = false }
                    )
                    .transition(.move(edge: .bottom))
                }
            }
        }
        .sheet(item: $selectedRoute) { route in
            RouteDetailScreen(route: route)
        }
        .sheet(isPresented: $showMenu) {
            MenuScreen()
        }
        .sheet(isPresented: $showFavorites) {
            FavoritesScreen()
        }
    }
}

struct YandexMapView: UIViewRepresentable {
    let routes: [Route]
    let selectedRoute: Route?
    let userLocation: CLLocation?

    func makeUIView(context: Context) -> YMKMapView {
        // Инициализатор в разных версиях SDK то nullable, то нет — так компилируется в обоих случаях.
        let created: YMKMapView? = YMKMapView(frame: .zero)
        guard let mapView = created else { fatalError("YMKMapView init failed") }
        context.coordinator.mapObjects = mapView.mapWindow.map.mapObjects.add()
        // Move to Norilsk
        let norilsk = YMKPoint(latitude: 69.34, longitude: 88.21)
        mapView.mapWindow.map.move(with: YMKCameraPosition(target: norilsk, zoom: 11, azimuth: 0, tilt: 0))
        return mapView
    }

    func updateUIView(_ uiView: YMKMapView, context: Context) {
        // Clear previous polylines
        guard let mapObjects = context.coordinator.mapObjects else { return }
        mapObjects.clear()

        // Draw routes
        for route in routes {
            let polyline = YMKPolyline(points: route.stops.map { YMKPoint(latitude: $0.lat, longitude: $0.lon) })
            let polylineObj = mapObjects.addPolyline(with: polyline)
            polylineObj.setStrokeColorWith(UIColor(route.colorArgb))
            polylineObj.strokeWidth = 4
            polylineObj.isVisible = (selectedRoute == nil || selectedRoute?.id == route.id) ? true : false
        }

        // Draw selected route polyline (if exists)
        if let route = selectedRoute {
            let polyline = YMKPolyline(points: route.polyline.map { YMKPoint(latitude: $0.lat, longitude: $0.lon) })
            let polylineObj = mapObjects.addPolyline(with: polyline)
            polylineObj.setStrokeColorWith(UIColor(route.colorArgb))
            polylineObj.strokeWidth = 6
            polylineObj.isVisible = true
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    class Coordinator {
        // Коллекция линий на карте этого YMKMapView; создаётся в makeUIView.
        var mapObjects: YMKMapObjectCollection?
    }
}

struct MapTopBar: View {
    let weather: Weather?
    let onMenuTap: () -> Void
    let onFavoritesTap: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onMenuTap) {
                Image(systemName: "line.horizontal.3")
                    .font(.title2)
                    .foregroundColor(.white)
                    .padding(8)
                    .background(Color.black.opacity(0.6), in: Circle())
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(formattedDate()).font(.headline).foregroundColor(.white)
                if let w = weather {
                    HStack(spacing: 6) {
                        Text("\(Int(w.tempC))°C").foregroundColor(.white)
                        Text("\(w.windMs) м/с").foregroundColor(.cyan)
                        Image(systemName: "arrow.clockwise").foregroundColor(.white)
                    }
                    .font(.subheadline)
                }
            }
            Spacer()
            Button(action: onFavoritesTap) {
                Image(systemName: "star")
                    .font(.title2)
                    .foregroundColor(.white)
                    .padding(8)
                    .background(Color.black.opacity(0.6), in: Circle())
            }
        }
        .padding(.horizontal)
        .padding(.top, 8)
    }

    private func formattedDate() -> String {
        let f = DateFormatter()
        f.dateFormat = "dd.MM HH:mm"
        return f.string(from: Date())
    }
}

// Заглушки экранов меню и избранного: в Android-версии это отдельные экраны,
// на iOS они ещё не перенесены.
struct MenuScreen: View {
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            Text("Меню появится в следующей версии")
                .foregroundColor(.secondary)
                .navigationTitle("Меню")
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Закрыть") { dismiss() }
                    }
                }
        }
    }
}

struct FavoritesScreen: View {
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            Text("Избранное появится в следующей версии")
                .foregroundColor(.secondary)
                .navigationTitle("Избранное")
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Закрыть") { dismiss() }
                    }
                }
        }
    }
}
