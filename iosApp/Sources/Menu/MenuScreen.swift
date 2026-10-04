// MenuScreen.swift — меню приложения: цифры по фактически загруженным данным.

import SwiftUI
import NorilskTransitCore

struct MenuScreen: View {
    @EnvironmentObject private var store: TransitStore
    @Environment(\.dismiss) private var dismiss

    private var variants: [RouteVariant] { store.index?.variants ?? [] }

    /// Без ключа сборка валидна, но тайлы не загрузятся — говорим об этом прямо.
    private var mapKeyState: String {
        let raw = Bundle.main.object(forInfoDictionaryKey: "YANDEX_MAPKIT_API_KEY") as? String ?? ""
        if raw.isEmpty || raw.hasPrefix("$(") {
            return "не задан"
        }
        return "задан"
    }

    private var appVersion: String {
        let short = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "—"
        return "\(short) (\(build))"
    }

    var body: some View {
        NavigationStack {
            List {
                Section("Данные") {
                    LabeledContent("Направлений", value: "\(variants.count)")
                    LabeledContent("С треком на карте", value: "\(variants.filter { $0.hasGeometry }.count)")
                    LabeledContent("Без трека", value: "\(variants.filter { !$0.hasGeometry }.count)")
                    LabeledContent("С расписанием", value: "\(variants.filter { $0.hasSchedule }.count)")
                    LabeledContent("Остановок", value: "\(store.index?.stops.count ?? 0)")
                    LabeledContent("Обновлено", value: store.index?.dataDate ?? "—")
                    LabeledContent("Работа офлайн", value: "да")
                }

                Section {
                    Text("Расписания — официальные данные МУП «Норильсктранс», встроены в приложение." +
                         " Геометрия и остановки есть не для всех направлений: где её нет, мы ничего не выдумываем." +
                         " Время показывается норильское (UTC+7).")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("Источник")
                }

                Section("О приложении") {
                    LabeledContent("Версия", value: appVersion)
                    LabeledContent("Карта", value: "Яндекс MapKit")
                    LabeledContent("Ключ карты", value: mapKeyState)
                }

                Section {
                    Text("Экран поддержки и политика конфиденциальности — на следующих этапах.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("Поддержка")
                }
            }
            .navigationTitle("Меню")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") { dismiss() }
                }
            }
        }
    }
}
