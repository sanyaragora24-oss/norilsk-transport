// FavoritesScreen.swift — экран избранного.
// Этап 1: минимальная реализация (экран требовался MapScreen, но не существовал —
// из-за этого проект не компилировался). Данные берутся из FavoritesStore/UserDefaults.

import SwiftUI

struct FavoritesScreen: View {
    @EnvironmentObject private var routesStore: RoutesStore
    @EnvironmentObject private var favoritesStore: FavoritesStore
    @Environment(\.dismiss) private var dismiss

    private var favoriteRoutes: [Route] {
        let ids = Set(favoritesStore.routes)
        return routesStore.routes.filter { ids.contains($0.id) }
    }

    private var favoriteStops: [Stop] {
        let ids = Set(favoritesStore.stops)
        var seen = Set<Int>()
        var result: [Stop] = []
        for stop in routesStore.routes.flatMap(\.stops) where ids.contains(stop.id) && seen.insert(stop.id).inserted {
            result.append(stop)
        }
        return result
    }

    var body: some View {
        NavigationStack {
            Group {
                if favoriteRoutes.isEmpty && favoriteStops.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "star")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                        Text("Пока нет избранного")
                            .font(.headline)
                        Text("Добавьте маршрут или остановку через звёздочку.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding()
                } else {
                    List {
                        if !favoriteRoutes.isEmpty {
                            Section("Маршруты") {
                                ForEach(favoriteRoutes) { route in
                                    HStack(spacing: 12) {
                                        Text(route.number)
                                            .font(.system(size: 18, weight: .black))
                                            .foregroundColor(.white)
                                            .frame(width: 44, height: 44)
                                            .background(Color(UIColor(route.colorArgb)), in: RoundedRectangle(cornerRadius: 10))
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(route.origin).font(.subheadline)
                                            Text("→ \(route.destination)").font(.caption).foregroundStyle(.secondary)
                                        }
                                    }
                                }
                            }
                        }

                        if !favoriteStops.isEmpty {
                            Section("Остановки") {
                                ForEach(favoriteStops) { stop in
                                    Label(stop.name, systemImage: "mappin.circle")
                                        .font(.subheadline)
                                }
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
        }
    }
}
