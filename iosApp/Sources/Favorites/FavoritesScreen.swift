// FavoritesScreen.swift — избранные маршруты и остановки.
//
// Хранилище — UserDefaults (FavoritesStore), сами объекты берём из индекса
// маршрутов: избранное переживает обновление данных и остаётся кликабельным.

import SwiftUI
import NorilskTransitCore

struct FavoritesScreen: View {
    @EnvironmentObject private var store: TransitStore
    @EnvironmentObject private var favorites: FavoritesStore
    @Environment(\.dismiss) private var dismiss

    private let onShowRoute: ((RouteVariant) -> Void)?
    private let onShowStop: ((StopInfo) -> Void)?

    init(onShowRoute: ((RouteVariant) -> Void)? = nil,
         onShowStop: ((StopInfo) -> Void)? = nil) {
        self.onShowRoute = onShowRoute
        self.onShowStop = onShowStop
    }

    var body: some View {
        NavigationStack {
            Group {
                if favoriteRoutes.isEmpty && favoriteStops.isEmpty {
                    PlaceholderStateView(
                        systemImage: "star",
                        title: "Пока нет избранного",
                        message: "Добавьте маршрут или остановку звёздочкой — в списке маршрутов, на экране маршрута или остановки."
                    )
                } else {
                    List {
                        if !favoriteRoutes.isEmpty {
                            Section("Маршруты") {
                                ForEach(favoriteRoutes) { variant in
                                    NavigationLink(value: variant) {
                                        RouteVariantRow(variant: variant, isFavorite: true)
                                    }
                                }
                                .onDelete(perform: deleteRoutes)
                            }
                        }
                        if !favoriteStops.isEmpty {
                            Section("Остановки") {
                                ForEach(favoriteStops) { stop in
                                    NavigationLink(value: stop) {
                                        StopListRow(stop: stop, isFavorite: true)
                                    }
                                }
                                .onDelete(perform: deleteStops)
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Избранное")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") { dismiss() }
                }
            }
            .navigationDestination(for: RouteVariant.self) { variant in
                RouteDetailView(variant: variant) { selected in
                    onShowRoute?(selected)
                    dismiss()
                }
            }
            .navigationDestination(for: StopInfo.self) { stop in
                StopDetailView(stop: stop) { selected in
                    onShowStop?(selected)
                    dismiss()
                }
            }
        }
    }

    private var favoriteRoutes: [RouteVariant] {
        let ids = Set(favorites.routes)
        return store.index?.variants.filter { ids.contains($0.id) } ?? []
    }

    private var favoriteStops: [StopInfo] {
        let ids = Set(favorites.stops)
        return store.index?.stops.filter { ids.contains($0.id) } ?? []
    }

    private func deleteRoutes(at offsets: IndexSet) {
        for index in offsets { favorites.toggleRoute(favoriteRoutes[index].id) }
    }

    private func deleteStops(at offsets: IndexSet) {
        for index in offsets { favorites.toggleStop(favoriteStops[index].id) }
    }
}
