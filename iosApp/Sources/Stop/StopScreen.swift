// StopScreen.swift — экран остановки с расписанием и будильником.

import SwiftUI
import CoreLocation
struct StopScreen: View {
    let stop: Stop
    let distance: String?

    @EnvironmentObject var locationManager: LocationManager
    @Environment(\.dismiss) var dismiss

    @State private var alarmEnabled = false
    @State private var alarmDistance: Double = 350  // meters

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text(stop.name).font(.title).bold()
                if let d = distance {
                    Text("\(d) от вас").foregroundColor(.secondary)
                }

                Divider()

                // Alarm toggle
                VStack(alignment: .leading) {
                    Toggle("Разбудить у этой остановки", isOn: $alarmEnabled)
                        .onChange(of: alarmEnabled) { _, on in
                            if on { scheduleAlarm() } else { cancelAlarm() }
                        }
                    Stepper("Радиус: \(Int(alarmDistance)) м", value: $alarmDistance, in: 100...1000, step: 50)
                        .onChange(of: alarmDistance) { _, v in
                            if alarmEnabled { scheduleAlarm() }
                        }
                }

                // Test signal button
                Button("Проверить сигнал") {
                    AudioServicesPlaySystemSound(1322)  // system alert sound
                }

                Spacer()
            }
            .padding()
            .navigationTitle(stop.name)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Назад") { dismiss() }
                }
            }
        }
    }

    private func scheduleAlarm() {
        // TODO: реализовать через CLCircularRegion + UNUserNotificationCenter
        // Регион радиуса alarmDistance вокруг остановки, при входе — локальное уведомление + звук
    }

    private func cancelAlarm() {
        // TODO: удалить регион из CLLocationManager
    }
}

import AudioToolbox
