// RouteDetailView.swift — экран направления маршрута: остановки и расписание.

import SwiftUI
import NorilskTransitCore

struct RouteDetailView: View {
    @EnvironmentObject private var store: TransitStore
    @EnvironmentObject private var favorites: FavoritesStore

    @State private var variant: RouteVariant
    @State private var tab: DetailTab = .stops
    @State private var dayType: ScheduleDayType

    private let onVariantChange: ((RouteVariant) -> Void)?
    private let onShowOnMap: ((RouteVariant) -> Void)?

    init(variant: RouteVariant,
         onVariantChange: ((RouteVariant) -> Void)? = nil,
         onShowOnMap: ((RouteVariant) -> Void)? = nil) {
        _variant = State(initialValue: variant)
        self.onVariantChange = onVariantChange
        self.onShowOnMap = onShowOnMap
        _tab = State(initialValue: variant.hasSchedule ? .schedule : .stops)
        _dayType = State(initialValue: ScheduleLogic.isWeekendNow() ? .weekend : .weekday)
    }

    private enum DetailTab: String, CaseIterable, Identifiable {
        case stops = "Остановки"
        case schedule = "Расписание"

        var id: String { rawValue }
    }

    var body: some View {
        VStack(spacing: 0) {
            header

            if siblings.count > 1 {
                directionMenu
            }

            Picker("Раздел", selection: $tab) {
                ForEach(DetailTab.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.bottom, 8)

            Group {
                switch tab {
                case .stops:
                    stopsContent
                case .schedule:
                    ScheduleTabView(variant: variant, dayType: $dayType)
                }
            }
            .frame(maxHeight: .infinity)
        }
        .navigationTitle("Маршрут №\(variant.number)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                if let onShowOnMap = onShowOnMap, variant.hasGeometry {
                    Button("На карту") { onShowOnMap(variant) }
                        .disabled(!variant.hasGeometry)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    favorites.toggleRoute(variant.id)
                } label: {
                    Image(systemName: favorites.routes.contains(variant.id) ? "star.fill" : "star")
                }
            }
        }
        .navigationDestination(for: StopInfo.self) { StopDetailView(stop: $0) }
    }

    // MARK: - Заголовок

    private var siblings: [RouteVariant] {
        store.index?.variants(number: variant.number) ?? [variant]
    }

    private var header: some View {
        HStack(spacing: 12) {
            RouteBadge(number: variant.number, colorArgb: variant.colorArgb, height: 56)
            VStack(alignment: .leading, spacing: 4) {
                Text(variant.title)
                    .font(.headline)
                    .lineLimit(3)
                HStack(spacing: 6) {
                    TagView(text: variant.hasGeometry ? "трек есть" : "трека нет",
                            color: variant.hasGeometry ? .green : .orange)
                    TagView(text: variant.hasSchedule ? "расписание есть" : "расписания нет",
                            color: variant.hasSchedule ? .green : .gray)
                }
            }
            Spacer()
        }
        .padding()
    }

    private var directionMenu: some View {
        HStack {
            Text("Направление")
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
            Menu {
                Picker("Направление", selection: Binding(
                    get: { variant },
                    set: { newValue in
                        variant = newValue
                        onVariantChange?(newValue)
                    }
                )) {
                    ForEach(siblings) { item in
                        Text(item.title).tag(item)
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(variant.title).font(.footnote).lineLimit(1)
                    Image(systemName: "chevron.up.chevron.down").font(.caption2)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color(.systemGray6), in: Capsule())
            }
        }
        .padding(.horizontal)
        .padding(.bottom, 4)
    }

    // MARK: - Остановки

    @ViewBuilder
    private var stopsContent: some View {
        if !variant.hasGeometry {
            VStack(alignment: .leading, spacing: 8) {
                Label("Геометрия направления не опубликована", systemImage: "exclamationmark.triangle")
                    .font(.subheadline)
                Text("МУП «Норильсктранс» не публикует трек этого направления, поэтому на карте его линии нет." +
                     " Остановки не выдумываем: их список появится, когда в данных появится трек." +
                     " Расписание по терминалам — официальное, см. вкладку «Расписание».")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Spacer()
            }
            .padding()
        } else if variant.stops.isEmpty {
            PlaceholderStateView(systemImage: "mappin.slash",
                                 title: "Остановки не указаны",
                                 message: "В данных маршрута нет списка остановок")
        } else {
            List {
                ForEach(Array(variant.stops.enumerated()), id: \.element.id) { index, stop in
                    if let info = store.index?.stop(id: stop.id) {
                        NavigationLink(value: info) {
                            HStack(spacing: 12) {
                                Text("\(index + 1)")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(.secondary)
                                    .frame(width: 28, alignment: .trailing)
                                Circle()
                                    .fill(Color(argb: variant.colorArgb))
                                    .frame(width: 10, height: 10)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(stop.name).font(.subheadline)
                                    Text("через остановку: \(info.routeIds.count) маршр.")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    } else {
                        HStack(spacing: 12) {
                            Text("\(index + 1)")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(.secondary)
                                .frame(width: 28, alignment: .trailing)
                            Text(stop.name).font(.subheadline)
                        }
                    }
                }
            }
            .listStyle(.plain)
        }
    }
}

// MARK: - Расписание

enum ScheduleDayType: String, CaseIterable, Identifiable {
    case weekday = "Будни"
    case weekend = "Выходные"

    var id: String { rawValue }
}

struct ScheduleTabView: View {
    let variant: RouteVariant
    @Binding var dayType: ScheduleDayType

    /// Текущее норильское время; обновляется по таймеру, чтобы обратный отсчёт не застывал.
    @State private var nowMinutes: Int = ScheduleLogic.minutesNow()
    private let ticker = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    private struct DepartureGroup: Identifiable {
        let terminal: String
        let times: [Int]
        var id: String { terminal }
    }

    var body: some View {
        Group {
            if let schedule = variant.schedule, schedule.hasSchedule, !schedule.timetable.isEmpty {
                List {
                    Section {
                        Picker("Дни", selection: $dayType) {
                            ForEach(ScheduleDayType.allCases) { Text($0.rawValue).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        Text("Время норильское (UTC+7). \(upcomingDescription)")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    Section("Ближайшие отправления") {
                        if upcoming.isEmpty {
                            Text("На сегодня отправлений по опубликованному расписанию больше нет")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(upcoming) { group in
                                if !group.times.isEmpty {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(group.terminal).font(.caption).foregroundStyle(.secondary)
                                        HStack(spacing: 8) {
                                            ForEach(group.times, id: \.self) { minutes in
                                                Text(ScheduleLogic.formatMinutes(minutes))
                                                    .font(.body.monospacedDigit())
                                                Text(ScheduleLogic.countdownText(departure: minutes,
                                                                                 nowMinutes: nowMinutes))
                                                    .font(.caption2)
                                                    .foregroundStyle(.secondary)
                                            }
                                            Spacer()
                                        }
                                    }
                                    .padding(.vertical, 2)
                                }
                            }
                        }
                    }

                    ForEach(schedule.timetable) { entry in
                        Section {
                            timesGrid(
                                times: dayType == .weekday ? entry.weekday : entry.weekend,
                                highlightFrom: dayType == todayDayType ? nowMinutes : nil
                            )
                        } header: {
                            Text(entry.terminal)
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .onReceive(ticker) { _ in
                    nowMinutes = ScheduleLogic.minutesNow()
                }
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    Label("Расписание не опубликовано", systemImage: "clock.badge.questionmark")
                        .font(.subheadline)
                    Text("Для направления «\(variant.title)» МУП «Норильсктранс» не публикует расписание" +
                         " (hasSchedule = false в данных). Времена отправления не выдумываем.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Spacer()
                }
                .padding()
            }
        }
    }

    /// Какой тип дня сегодня в Норильске (нужно, чтобы не подсвечивать
    /// «ближайшее» в колонке, которую пользователь просто просматривает).
    private var todayDayType: ScheduleDayType {
        ScheduleLogic.isWeekendNow() ? .weekend : .weekday
    }

    private var upcomingDescription: String {
        "Сейчас \(ScheduleLogic.formatMinutes(nowMinutes))."
    }

    private var upcoming: [DepartureGroup] {
        guard let schedule = variant.schedule else { return [] }
        let now = nowMinutes
        return schedule.timetable.map { entry in
            let times = dayType == .weekday ? entry.weekday : entry.weekend
            return DepartureGroup(
                terminal: entry.terminal,
                times: ScheduleLogic.nextDepartures(times: times, nowMinutes: now, limit: 3)
            )
        }
    }

    @ViewBuilder
    private func timesGrid(times: [String], highlightFrom: Int? = nil) -> some View {
        let minutes = ScheduleLogic.normalizedTimes(times)
        if minutes.isEmpty {
            Text("Нет данных").font(.footnote).foregroundStyle(.secondary)
        } else {
            // Подсвечиваем ближайший рейс, но только если смотрим сегодняшний день
            let next = highlightFrom.flatMap { now in minutes.first { $0 >= now } }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 58), spacing: 8)], spacing: 8) {
                ForEach(minutes, id: \.self) { value in
                    Text(ScheduleLogic.formatMinutes(value))
                        .font(.footnote.monospacedDigit())
                        .padding(.vertical, 4)
                        .frame(maxWidth: .infinity)
                        .background(
                            value == next ? Color.accentColor.opacity(0.3) : Color(.systemGray6),
                            in: RoundedRectangle(cornerRadius: 6)
                        )
                }
            }
            .padding(.vertical, 2)
        }
    }
}
