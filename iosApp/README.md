# iosApp

iOS-версия приложения «Норильский транспорт» (SwiftUI + Яндекс MapKit).

## Структура

Проект генерируется XcodeGen из `project.yml`, поэтому в git лежит не `.xcodeproj`,
а описание проекта. Три таргета:

| Таргет | Тип | Что внутри |
| --- | --- | --- |
| `NorilskTransit` | application | UI: карта, маршруты, остановки, избранное, меню |
| `NorilskTransitCore` | library.static | модели, парсеры JSON, индекс, логика расписаний (только Foundation) |
| `NorilskTransitCoreTests` | bundle.unit-test | unit-тесты Core |

`NorilskTransitCore` выделен отдельным таргетом специально: он не зависит от
UIKit/MapKit/SwiftUI, поэтому тесты — «логические», без host-приложения:
не запускают приложение и не инициализируют MapKit (в CI нет валидного API-ключа).

```
NorilskTransitApp.swift      entry point (@main), инициализация MapKit
Core/                        модуль NorilskTransitCore
  Models.swift               Route, Stop, ScheduleEntry, RouteVariant, StopInfo
  Parsing.swift              парсеры norilsk_routes.json / norilsk_schedule.json
  ScheduleLogic.swift        разбор HH:mm, будни/выходные, ближайшие отправления
  TransitIndex.swift         объединение маршрутов и расписаний, индекс остановок, поиск
Sources/
  App/TransitStore.swift     загрузка JSON из бандла и сборка индекса
  App/LocationManager.swift  CoreLocation + FavoritesStore (UserDefaults)
  Map/MapScreen.swift        карта: линии, маркеры остановок, слой геолокации
  Routes/RouteListScreen.swift   список маршрутов и остановок, поиск
  Routes/RouteDetailView.swift   направление: остановки + расписание
  Stop/StopDetailView.swift      остановка: какие маршруты через неё идут
  Favorites/, Menu/, UI/
Tests/                       unit-тесты Core
Resources/                   norilsk_routes.json, norilsk_schedule.json, PrivacyInfo.xcprivacy
```

## Данные

Оба JSON лежат в `Resources/` и попадают внутрь `.app` (это проверяется в CI).

- `norilsk_routes.json` — 54 направления с геометрией (трек + остановки).
- `norilsk_schedule.json` — 62 записи расписаний, 9152 времени отправления, `dataDate: 11.06.2026`.
- 331 уникальная остановка (индекс строится только по направлениям с треком).

**8 записей расписаний существуют без геометрии** — их не удаляем и не объединяем,
а показываем с честной пометкой «трека нет»:

| Ключи | Маршрут | Примечание |
| --- | --- | --- |
| `3467:0…3467:5` | 31Э | геометрии в данных нет вообще, расписания у всех шести одинаковые |
| `2246:0`, `2246:1` | 31 | старые направления; с треком только `2246:2…2246:5` |

Ещё у 8 направлений `hasSchedule = false` (27К, 22И, 31Б, 30К, 40К) — вместо времён
показывается сообщение, что МУП не публикует расписание. Ничего не выдумывается:
нет данных — нет и строк.

Поддерживаются оба формата файла расписаний: новый (массив `timetable` по терминалам)
и старый (`terminal`/`weekday`/`weekend` на верхнем уровне). Время показывается
норильское (UTC+7, `Asia/Krasnoyarsk`) независимо от часового пояса устройства.
Переноса «на завтра» нет: если на сегодня рейсов не осталось, так и пишем.

## Сборка локально (нужен Mac + Xcode 16)

```bash
brew install xcodegen
cd iosApp
xcodegen generate
pod install
open NorilskTransit.xcworkspace
```

Запуск тестов:

```bash
xcodebuild -workspace NorilskTransit.xcworkspace -scheme NorilskTransit \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' test
```

В Xcode: Signing & Capabilities → Team → Run. Ключ карты берётся из `Info.plist`
(`YANDEX_MAPKIT_API_KEY`), значение подставляется из переменной окружения;
без ключа сборка валидна, но тайлы не загрузятся.

## CI

Workflow `.github/workflows/ios-build.yml` (macos-14, Xcode 16.2):

1. выбор Xcode (с принятием лицензии и фолбэком, если версия в образе изменилась);
2. `xcodegen generate` → `pod install` (без `--repo-update`);
3. проверка исходных JSON;
4. `xcodebuild build` под iOS Simulator;
5. `xcodebuild test` на симуляторе, который создаётся на лету;
6. проверка `.app`: версия, ресурсы, **что JSON внутри читаются**;
7. артефакт `.app` + логи.

Любая ошибка `xcodebuild` роняет workflow; отсутствие `.app` или данных в нём —
тоже ошибка. Диагностика падений публикуется как annotations (у шага GitHub их
максимум 10, поэтому логи тестов идут раньше логов сборки, а `ld: warning` MapKit
отфильтрованы).

## Что осталось

1. **Yandex MapKit API key** — секрет `YANDEX_MAPKIT_API_KEY` в репозитории.
2. Проверить приложение вживую на устройстве/симуляторе (в CI прогоняются только
   сборка и тесты).
3. Будильник у остановки (`CLCircularRegion` + `UNUserNotificationCenter`) — не входит
   в текущий этап.
4. Голосовое оповещение, Firebase/FCM/Crashlytics, полноценный RoutePlanner — дальше.
5. Иконки и релиз в App Store.
6. VoiceOver / Dynamic Type — не проверялись.

## Известные ограничения

- Расстояние до остановки считается по прямой (гаверсинус), без учёта дорог.
- В данных есть огрехи: 2 остановки с разными названиями для одного id (22649, 22650)
  и 10 id с разными координатами в разных маршрутах — индекс берёт первое вхождение.
- У направлений без трека нет списка остановок — это свойство данных.
- Версия приложения 1.2.6 (8), Android — 1.2.9.
