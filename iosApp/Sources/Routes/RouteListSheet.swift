// RouteListSheet.swift — список маршрутов с поиском, как Android CommonUi.RouteCard.

import SwiftUI

struct RouteListSheet: View {
    let routes: [Route]
    var onSelect: (Route) -> Void
    var onClose: () -> Void

    @State private var searchText = ""
    @State private var tab: Tab = .routes

    enum Tab: String, CaseIterable {
        case routes = "Маршруты"
        case stops = "Остановки"
    }

    var body: some View {
        VStack(spacing: 0) {
            // Search bar
            HStack {
                Image(systemName: "magnifyingglass").foregroundColor(.secondary)
                TextField("Поиск маршрута или остановки", text: $searchText)
                    .textFieldStyle(.plain)
            }
            .padding(12)
            .background(Color(.systemGray6), in: RoundedRectangle(cornerRadius: 12))
            .padding()

            // Tabs
            HStack {
                ForEach(Tab.allCases, id: \.self) { t in
                    Button(t.rawValue) {
                        tab = t
                    }
                    .foregroundColor(tab == t ? .accentColor : .secondary)
                    .padding(.bottom, 8)
                    .overlay(alignment: .bottom) {
                        if tab == t {
                            Rectangle().fill(Color.accentColor).frame(height: 2)
                        }
                    }
                    Spacer()
                }
            }
            .padding(.horizontal)

            Divider()

            // List
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 6) {
                    ForEach(filteredRoutes) { route in
                        RouteRow(route: route, onTap: { onSelect(route) })
                    }
                }
                .padding()
            }

            Spacer()

            Button("Закрыть", action: onClose)
                .padding()
                .frame(maxWidth: .infinity)
                .background(Color(.systemGray6))
        }
        .background(Color(.systemBackground))
    }

    private var filteredRoutes: [Route] {
        if searchText.isEmpty { return routes }
        let q = searchText.lowercased()
        return routes.filter {
            $0.number.lowercased().contains(q) ||
            $0.origin.lowercased().contains(q) ||
            $0.destination.lowercased().contains(q)
        }
    }
}

struct RouteRow: View {
    let route: Route
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                // Route badge
                Text(route.number)
                    .font(.system(size: 22, weight: .black))
                    .foregroundColor(.white)
                    .frame(width: 56, height: 56)
                    .background(Color(route.colorArgb), in: RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.black, lineWidth: 2.5)
                    )
                    .shadow(color: .black.opacity(0.6), radius: 3, x: 0, y: 1)

                VStack(alignment: .leading, spacing: 4) {
                    Text(route.origin).font(.headline)
                    Text("→ \(route.destination)")
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color(.systemGray5), in: Capsule())
                }
                Spacer()
            }
            .padding(8)
        }
        .buttonStyle(.plain)
    }
}
