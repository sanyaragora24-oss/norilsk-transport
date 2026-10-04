// TransitStore.swift — загрузка norilsk_routes.json + norilsk_schedule.json
// и сборка поискового индекса. Пришёл на смену RoutesStore (там читался
// только routes.json, расписание было заглушкой).

import Foundation
import NorilskTransitCore

final class TransitStore: ObservableObject {
    @Published private(set) var index: TransitIndex?
    @Published private(set) var isLoading = true
    @Published private(set) var errorMessage: String?

    init(bundle: Bundle = .main) {
        load(from: bundle)
    }

    func load(from bundle: Bundle = .main) {
        isLoading = true
        defer { isLoading = false }

        do {
            let routesData = try BundleDataLoader.loadData(resourceName: "norilsk_routes", bundle: bundle)
            let scheduleData = try BundleDataLoader.loadData(resourceName: "norilsk_schedule", bundle: bundle)
            let routes = try RoutesParser.parse(routesData)
            let schedule = try ScheduleParser.parse(scheduleData)
            index = TransitIndex(routes: routes, schedule: schedule)
            errorMessage = nil
        } catch {
            index = nil
            errorMessage = error.localizedDescription
        }
    }
}
