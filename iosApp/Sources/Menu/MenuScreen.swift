// MenuScreen.swift — экран меню.
// Этап 1: минимальная реализация (экран требовался MapScreen, но не существовал —
// из-за этого проект не компилировался). Наполнение — на следующих этапах.

import SwiftUI

struct MenuScreen: View {
    @Environment(\.dismiss) private var dismiss

    private var appVersion: String {
        let short = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "—"
        return "\(short) (\(build))"
    }

    var body: some View {
        NavigationStack {
            List {
                Section("Данные") {
                    LabeledContent("Маршруты и расписания", value: "встроены")
                    LabeledContent("Работа офлайн", value: "да")
                }

                Section("О приложении") {
                    LabeledContent("Версия", value: appVersion)
                    LabeledContent("Карта", value: "Яндекс MapKit")
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
