// RouteListScreen.swift — список маршрутов и остановок с поиском.
//
// Открывается кнопкой «Маршруты» на карте. Внутри — своя навигация:
// маршрут -> остановка -> маршрут (и наоборот).

import SwiftUI
import NorilskTransitCore

struct RouteListScreen: View {
    @EnvironmentObject private var store: TransitStore
    @EnvironmentObject private var favorites: FavoritesStore
    @Environment(\.dismiss) private var dismiss

    @State private var query = ""
    @State private var tab: ListTab = .routes

    /// Показ маршрута на карте (закрывает список и выделяет линию).
    private let onShowRoute: ((RouteVariant) -> Void)?
    /// Показ остановки на карте (закрывает список и центрует камеру).
    private let onShowStop: ((StopInfo) -> Void)?

    init(onShowRoute: ((RouteVariant) -> Void)? = nil,
         onShowStop: ((StopInfo) -> Void)? = nil) {
        self.onShowRoute = onShowRoute
        self.onShowStop = onShowStop
    }

    private enum ListTab: String, CaseIterable, Identifiable {
        case routes = "Маршруты"
        case stops = "Остановки"

        var id: String { rawValue }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                searchBar
                tabPicker
                Divider()
                content
            }
            .navigationTitle("Транспорт Норильска")
            .navigationBarTitleDisplayMode(.inline)
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

    // MARK: - Элементы

    private var searchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField(tab == .routes ? "Номер маршрута или остановка" : "Название остановки",
                      text: $query)
                .textFieldStyle(.plain)
                .autocorrectionDisabled()
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                }
            }
        }
        .padding(10)
        .background(Color(.systemGray6), in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal)
        .padding(.top, 8)
    }

    private var tabPicker: some View {
        Picker("Раздел", selection: $tab) {
            ForEach(ListTab.allCases) { Text($0.rawValue).tag($0) }
        }
        .pickerStyle(.segmented)
        .padding()
    }

    @ViewBuilder
    private var content: some View {
        if store.index == nil {
            PlaceholderStateView(
                systemImage: "exclamationmark.triangle",
                title: "Данные не загрузились",
                message: store.errorMessage
            )
        } else if tab == .routes {
            routesList
        } else {
            stopsList
        }
    }

    // MARK: - Маршруты

    private var groups: [(String, [RouteVariant])] {
        guard let index = store.index else { return [] }
        let found = index.searchRoutes(query)
        var grouped: [String: [RouteVariant]] = [:]
        for variant in found { grouped[variant.number, default: []].append(variant) }
        return index.numbers.compactMap { number in
            guard let variants = grouped[number] else { return nil }
            return (number, variants)
        }
    }

    private var routesList: some View {
        List {
            if groups.isEmpty {
                Text("Ничего не найдено").foregroundStyle(.secondary)
            }
            ForEach(groups, id: \.0) { number, variants in
                Section {
                    ForEach(variants) { variant in
                        NavigationLink(value: variant) {
                            RouteVariantRow(variant: variant, isFavorite: favorites.routes.contains(variant.id))
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button {
                                favorites.toggleRoute(variant.id)
                            } label: {
                                Label(favorites.routes.contains(variant.id) ? "Убрать" : "В избранное",
                                      systemImage: favorites.routes.contains(variant.id) ? "star.slash" : "star")
                            }
                            .tint(.yellow)
                        }
                    }
                } header: {
                    HStack(spacing: 6) {
                        Text("№ \(number)").font(.headline)
                        Text("· \(variants.count) напр.").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            if let date = store.index?.dataDate {
                Section {
                    Text("Данные расписаний МУП «Норильсктранс» на \(date)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    // MARK: - Остановки

    private var filteredStops: [StopInfo] {
        store.index?.searchStops(query) ?? []
    }

    private var stopsList: some View {
        List {
            if filteredStops.isEmpty {
                Text("Ничего не найдено").foregroundStyle(.secondary)
            }
            ForEach(filteredStops) { stop in
                NavigationLink(value: stop) {
                    StopListRow(stop: stop, isFavorite: favorites.stops.contains(stop.id))
                }
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button {
                        favorites.toggleStop(stop.id)
                    } label: {
                        Label(favorites.stops.contains(stop.id) ? "Убрать" : "В избранное",
                              systemImage: favorites.stops.contains(stop.id) ? "star.slash" : "star")
                    }
                    .tint(.yellow)
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

// MARK: - Строки списков

struct RouteVariantRow: View {
    let variant: RouteVariant
    var isFavorite: Bool = false

    var body: some View {
        HStack(spacing: 12) {
            RouteBadge(number: variant.number, colorArgb: variant.colorArgb)

            VStack(alignment: .leading, spacing: 4) {
                Text(variant.title)
                    .font(.subheadline)
                    .lineLimit(2)
                HStack(spacing: 6) {
                    if !variant.hasGeometry {
                        TagView(text: "нет трека", color: .orange)
                    }
                    if variant.hasSchedule {
                        TagView(text: "расписание", color: .green)
                    } else {
                        TagView(text: "без расписания", color: .gray)
                    }
                    if isFavorite {
                        Image(systemName: "star.fill").font(.caption2).foregroundStyle(.yellow)
                    }
                }
            }
        }
        .padding(.vertical, 2)
    }
}

struct StopListRow: View {
    let stop: StopInfo
    var isFavorite: Bool = false
    var distanceText: String?

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "mappin.circle.fill")
                .foregroundStyle(.blue)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(stop.name).font(.subheadline)
                    if isFavorite {
                        Image(systemName: "star.fill").font(.caption2).foregroundStyle(.yellow)
                    }
                }
                Text(routesText)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if let distanceText {
                Text(distanceText)
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private var routesText: String {
        let count = stop.routeIds.count
        if count == 0 { return "нет маршрутов с треком" }
        return "маршрутов: \(count)"
    }
}

struct TagView: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.caption2)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.18), in: Capsule())
            .foregroundStyle(color)
    }
}
