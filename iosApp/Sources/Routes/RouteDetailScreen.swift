// RouteDetailScreen.swift — детали маршрута: остановки, расписание, будильник.

import SwiftUI

struct RouteDetailScreen: View {
    let route: Route

    @EnvironmentObject var favoritesStore: FavoritesStore
    @Environment(\.dismiss) var dismiss

    @State private var showSchedule = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                header

                Picker("", selection: $showSchedule) {
                    Text("На карте").tag(false)
                    Text("Расписание").tag(true)
                }
                .pickerStyle(.segmented)
                .padding()

                if showSchedule {
                    ScheduleView(routeId: route.id)
                } else {
                    StopsList(stops: route.stops)
                }
            }
            .navigationTitle("Маршрут №\(route.number)")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Закрыть") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        favoritesStore.toggleRoute(route.id)
                    } label: {
                        Image(systemName: favoritesStore.routes.contains(route.id) ? "star.fill" : "star")
                    }
                }
            }
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            Text(route.number)
                .font(.system(size: 28, weight: .black))
                .foregroundColor(.white)
                .frame(width: 64, height: 64)
                // Color(UInt32) не существует — конвертим через UIColor
                .background(Color(UIColor(route.colorArgb)), in: RoundedRectangle(cornerRadius: 14))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.black, lineWidth: 3)
                )
                .shadow(color: .black.opacity(0.6), radius: 3, x: 0, y: 1)

            VStack(alignment: .leading) {
                Text(route.origin).font(.headline)
                Text("напр. \(route.destination)").font(.caption).foregroundColor(.secondary)
            }
            Spacer()
        }
        .padding()
    }
}

struct StopsList: View {
    let stops: [Stop]

    var body: some View {
        List(stops) { stop in
            HStack {
                Circle().fill(Color.blue).frame(width: 12, height: 12)
                Text(stop.name).font(.body)
            }
        }
        .listStyle(.plain)
    }
}

struct ScheduleView: View {
    let routeId: String
    @State private var weekend = false

    var body: some View {
        VStack(alignment: .leading) {
            HStack {
                Button("Будни") { weekend = false }
                    .foregroundColor(weekend ? .secondary : .accentColor)
                Spacer()
                Button("Выходные") { weekend = true }
                    .foregroundColor(weekend ? .accentColor : .secondary)
            }
            .padding()

            ScrollView {
                // TODO: load from norilsk_schedule.json
                Text("Расписание уточняется")
                    .padding()
            }
        }
    }
}
