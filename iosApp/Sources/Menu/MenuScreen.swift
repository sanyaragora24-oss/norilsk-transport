// MenuScreen.swift — меню приложения: цифры по фактически загруженным данным.

import SwiftUI
import NorilskTransitCore

struct MenuScreen: View {
    @EnvironmentObject private var store: TransitStore
    @Environment(\.dismiss) private var dismiss

    private var variants: [RouteVariant] { store.index?.variants ?? [] }

    /// Без ключа сборка валидна, но тайлы не загрузятся — говорим об этом прямо.
    /// Значение ключа не показываем никогда: только состояние (см. MapKitKey).
    private var mapKeyState: String { MapKitKey.currentState.title }

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

                if MapKitKey.currentState == .missing {
                    Section("Ключ карты") {
                        Text("Ключ Яндекс.Карт не задан: подложка карты (тайлы) не загрузится, " +
                             "но линии маршрутов, остановки и расписания работают — они из встроенных JSON. " +
                             "Как задать ключ локально: iosApp/README.md, раздел «Ключ карты».")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
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
