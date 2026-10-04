// MapScreen.swift — главный экран: карта Яндекс + доступ к маршрутам и остановкам.

import SwiftUI
import UIKit
import YandexMapsMobile
import NorilskTransitCore

struct MapScreen: View {
    @EnvironmentObject private var store: TransitStore
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var locationManager: LocationManager

    @State private var selectedVariant: RouteVariant?
    @State private var detailVariant: RouteVariant?
    @State private var selectedStop: StopInfo?
    @State private var showRouteList = false
    @State private var showFavorites = false
    @State private var showMenu = false
    @State private var centerOnUser = false
    @State private var centerOnStop: MapStopOverlay?

    var body: some View {
        ZStack(alignment: .bottom) {
            TransitMapView(
                routes: mapRoutes,
                stops: mapStops,
                fitRouteId: selectedVariant?.id,
                centerOnUser: $centerOnUser,
                centerOnStop: $centerOnStop,
                onStopTap: { stopId in
                    selectedStop = store.index?.stop(id: stopId)
                },
                onRouteTap: { routeId in
                    guard let variant = store.index?.variant(id: routeId) else { return }
                    selectedVariant = variant
                }
            )
            .ignoresSafeArea()

            VStack(spacing: 8) {
                MapTopBar(
                    weather: locationManager.weather,
                    onMenuTap: { showMenu = true },
                    onFavoritesTap: { showFavorites = true }
                )

                if let message = store.errorMessage {
                    Text(message)
                        .font(.footnote)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(.red.opacity(0.85), in: Capsule())
                        .foregroundStyle(.white)
                }

                Spacer()

                if let variant = selectedVariant {
                    SelectedRouteCard(
                        variant: variant,
                        onOpen: { detailVariant = variant },
                        onClose: { selectedVariant = nil }
                    )
                }

                mapButtons
            }
            .padding()
        }
        .sheet(isPresented: $showRouteList) {
            RouteListScreen(
                onShowRoute: { variant in
                    selectedVariant = variant
                },
                onShowStop: { stop in
                    centerOnStop = MapStopOverlay(id: stop.id, lat: stop.lat, lon: stop.lon, title: stop.name)
                }
            )
        }
        .sheet(item: $detailVariant) { variant in
            NavigationStack {
                RouteDetailView(variant: variant) { updated in
                    selectedVariant = updated
                }
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Готово") { detailVariant = nil }
                    }
                }
            }
        }
        .sheet(item: $selectedStop) { stop in
            NavigationStack {
                StopDetailView(stop: stop) { selected in
                    selectedStop = nil
                    centerOnStop = MapStopOverlay(id: selected.id, lat: selected.lat, lon: selected.lon, title: selected.name)
                }
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button("Готово") { selectedStop = nil }
                        }
                    }
            }
        }
        .sheet(isPresented: $showFavorites) {
            FavoritesScreen(
                onShowRoute: { variant in
                    selectedVariant = variant
                },
                onShowStop: { stop in
                    centerOnStop = MapStopOverlay(id: stop.id, lat: stop.lat, lon: stop.lon, title: stop.name)
                }
            )
        }
        .sheet(isPresented: $showMenu) { MenuScreen() }
    }

    // MARK: - Данные для карты

    private var mapRoutes: [MapRouteOverlay] {
        guard let index = store.index else { return [] }
        if let selected = selectedVariant {
            guard selected.hasGeometry else { return [] }
            return [
                MapRouteOverlay(id: selected.id,
                                points: selected.polyline,
                                color: UIColor(argb: selected.colorArgb),
                                isSelected: true)
            ]
        }
        return index.variants
            .filter { $0.hasGeometry }
            .map { MapRouteOverlay(id: $0.id, points: $0.polyline, color: UIColor(argb: $0.colorArgb), isSelected: false) }
    }

    private var mapStops: [MapStopOverlay] {
        guard let selected = selectedVariant, selected.hasGeometry else { return [] }
        return selected.stops.map { MapStopOverlay(id: $0.id, lat: $0.lat, lon: $0.lon, title: $0.name) }
    }

    // MARK: - Кнопки

    private var mapButtons: some View {
        HStack(alignment: .bottom, spacing: 12) {
            Button {
                showRouteList = true
            } label: {
                Label("Маршруты", systemImage: "list.bullet")
                    .font(.headline)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .background(.blue, in: Capsule())
                    .foregroundStyle(.white)
            }

            Spacer()

            Button {
                centerOnUser = true
            } label: {
                Image(systemName: "location.fill")
                    .font(.title3)
                    .padding(12)
                    .background(.ultraThinMaterial, in: Circle())
            }
        }
    }
}

// MARK: - Карточка выбранного маршрута

struct SelectedRouteCard: View {
    let variant: RouteVariant
    let onOpen: () -> Void
    let onClose: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            RouteBadge(number: variant.number, colorArgb: variant.colorArgb, height: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(variant.title)
                    .font(.subheadline)
                    .lineLimit(2)
                HStack(spacing: 6) {
                    TagView(text: variant.hasSchedule ? "расписание есть" : "расписаний нет",
                            color: variant.hasSchedule ? .green : .gray)
                    if !variant.hasGeometry {
                        TagView(text: "трека нет", color: .orange)
                    }
                }
            }
            Spacer()
            Button("Подробнее", action: onOpen)
                .font(.footnote)
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(12)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - Обёртка карты

struct MapRouteOverlay: Hashable {
    let id: String
    let points: [LatLon]
    let color: UIColor
    let isSelected: Bool
}

struct MapStopOverlay: Hashable {
    let id: Int
    let lat: Double
    let lon: Double
    let title: String
}

struct TransitMapView: UIViewRepresentable {
    let routes: [MapRouteOverlay]
    let stops: [MapStopOverlay]
    let fitRouteId: String?
    @Binding var centerOnUser: Bool
    @Binding var centerOnStop: MapStopOverlay?
    let onStopTap: (Int) -> Void
    /// Тап по линии маршрута: возвращает id направления.
    let onRouteTap: ((String) -> Void)?

    private static let norilskCenter = YMKPoint(latitude: 69.34, longitude: 88.21)

    func makeCoordinator() -> Coordinator {
        Coordinator(onStopTap: onStopTap, onRouteTap: onRouteTap)
    }

    func makeUIView(context: Context) -> YMKMapView {
        // YMKMapView(frame:) приходит из ObjC как failable init -> YMKMapView?
        let mapView = YMKMapView(frame: .zero)!
        mapView.mapWindow.map.move(
            with: YMKCameraPosition(target: Self.norilskCenter, zoom: 11, azimuth: 0, tilt: 0)
        )

        // Слой геолокации. MapKit хранит слой по слабой ссылке,
        // поэтому держим его в координаторе.
        let userLayer = YMKMapKit.sharedInstance().createUserLocationLayer(with: mapView.mapWindow)
        userLayer.setVisibleWithOn(true)
        context.coordinator.userLayer = userLayer

        return mapView
    }

    func updateUIView(_ mapView: YMKMapView, context: Context) {
        context.coordinator.onStopTap = onStopTap
        context.coordinator.onRouteTap = onRouteTap

        let map = mapView.mapWindow.map
        let objects = map.mapObjects
        objects.clear()

        // Линии маршрутов
        for overlay in routes where overlay.points.count >= 2 {
            let points = overlay.points.map { YMKPoint(latitude: $0.lat, longitude: $0.lon) }
            let polyline = objects.addPolyline(with: YMKPolyline(points: points))
            // У YMKPolylineMapObject нет свойства strokeColor — только setStrokeColorWith(_:)
            polyline.setStrokeColorWith(overlay.color)
            polyline.strokeWidth = overlay.isSelected ? 6 : 3.5
            polyline.zIndex = overlay.isSelected ? 10 : 1
            // Тап по линии выделяет маршрут (навигация «карта -> маршрут»)
            polyline.userData = overlay.id
            polyline.addTapListener(with: context.coordinator)
        }

        // Остановки выбранного маршрута
        for stop in stops {
            let placemark = objects.addPlacemark(with: YMKPoint(latitude: stop.lat, longitude: stop.lon))
            placemark.userData = stop.id
            placemark.setTextWithText(stop.title)
            placemark.zIndex = 20
            placemark.addTapListener(with: context.coordinator)
        }

        // Камера: подгоняем под выбранный маршрут, но только при его смене,
        // иначе карта перескакивала бы при каждом обновлении состояния.
        if context.coordinator.lastFitRouteId != fitRouteId {
            context.coordinator.lastFitRouteId = fitRouteId
            if let fitRouteId = fitRouteId,
               let overlay = routes.first(where: { $0.id == fitRouteId }),
               overlay.points.count >= 2 {
                let points = overlay.points.map { YMKPoint(latitude: $0.lat, longitude: $0.lon) }
                let geometry = YMKGeometry(polyline: YMKPolyline(points: points))
                let position = map.cameraPosition(with: geometry)
                map.move(with: position, animation: YMKAnimation(type: .smooth, duration: 0.6))
            }
        }

        // Центровка на пользователя по кнопке
        if centerOnUser {
            if let position = context.coordinator.userLayer?.cameraPosition() {
                map.move(with: position, animation: YMKAnimation(type: .smooth, duration: 0.4))
            }
            DispatchQueue.main.async { centerOnUser = false }
        }

        // Центровка на остановке (переход «на карту» из списков)
        if let stop = centerOnStop {
            let position = YMKCameraPosition(
                target: YMKPoint(latitude: stop.lat, longitude: stop.lon),
                zoom: 16,
                azimuth: 0,
                tilt: 0
            )
            map.move(with: position, animation: YMKAnimation(type: .smooth, duration: 0.4))
            DispatchQueue.main.async { centerOnStop = nil }
        }
    }

    final class Coordinator: NSObject, YMKMapObjectTapListener {
        var onStopTap: (Int) -> Void
        var onRouteTap: ((String) -> Void)?
        var userLayer: YMKUserLocationLayer?
        var lastFitRouteId: String?

        init(onStopTap: @escaping (Int) -> Void, onRouteTap: ((String) -> Void)? = nil) {
            self.onStopTap = onStopTap
            self.onRouteTap = onRouteTap
            super.init()
        }

        func onMapObjectTap(with mapObject: YMKMapObject, point: YMKPoint) -> Bool {
            // Тип объекта определяем по userData: Int — остановка, String — маршрут
            if let stopId = mapObject.userData as? Int {
                onStopTap(stopId)
                return true
            }
            if let routeId = mapObject.userData as? String {
                onRouteTap?(routeId)
                return true
            }
            return false
        }
    }
}

// MARK: - Верхняя панель

struct MapTopBar: View {
    let weather: Weather?
    let onMenuTap: () -> Void
    let onFavoritesTap: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onMenuTap) {
                Image(systemName: "line.horizontal.3")
                    .font(.title2)
                    .foregroundStyle(.white)
                    .padding(8)
                    .background(.black.opacity(0.6), in: Circle())
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(formattedDate()).font(.headline).foregroundStyle(.white)
                if let weather {
                    HStack(spacing: 6) {
                        Text("\(Int(weather.tempC))°C").foregroundStyle(.white)
                        Text("\(weather.windMs) м/с").foregroundStyle(.cyan)
                    }
                    .font(.subheadline)
                }
            }
            Spacer()
            Button(action: onFavoritesTap) {
                Image(systemName: "star")
                    .font(.title2)
                    .foregroundStyle(.white)
                    .padding(8)
                    .background(.black.opacity(0.6), in: Circle())
            }
        }
        .padding(.horizontal)
        .padding(.top, 8)
    }

    private func formattedDate() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd.MM HH:mm"
        formatter.timeZone = ScheduleLogic.norilskTimeZone()
        return formatter.string(from: Date())
    }
}
