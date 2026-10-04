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
        let mapView = YMKMapView(frame: .zero)
        // NOTE: YMKMapView не имеет свойства mapType (было `mapView.mapType = .map` —
        // такой вызов не компилируется). Тип карты задаётся через YMKMap.mapType при необходимости.
        let norilsk = YMKPoint(latitude: 69.34, longitude: 88.21)
        mapView.mapWindow.map.move(with: YMKCameraPosition(target: norilsk, zoom: 11, azimuth: 0, tilt: 0))
        return mapView
    }

    func updateUIView(_ uiView: YMKMapView, context: Context) {
        // ВАЖНО: объекты нужно добавлять в коллекцию ВИДИМОЙ карты.
        // Раньше Coordinator создавал отдельный YMKMapView() и рисовал в него —
        // на экране не появлялось ничего.
        let mapObjects = uiView.mapWindow.map.mapObjects
        mapObjects.clear()

        for route in routes {
            let isSelected = selectedRoute?.id == route.id
            let isVisible = selectedRoute == nil || isSelected

            // Геометрия по реальным дорогам (OSM)
            if route.polyline.count >= 2 {
                let points = route.polyline.map { YMKPoint(latitude: $0.lat, longitude: $0.lon) }
                let polyline = YMKPolyline(points: points)
                let polylineObj = mapObjects.addPolyline(with: polyline)
                // У YMKPolylineMapObject нет свойства strokeColor — только setStrokeColorWith(_:)
                polylineObj.setStrokeColorWith(UIColor(route.colorArgb))
                polylineObj.strokeWidth = isSelected ? 6 : 4
                polylineObj.isVisible = isVisible
            }
        }
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
