# iosApp

SwiftUI каркас iOS-версии приложения «Норильский транспорт».

## Структура

- `NorilskTransitApp.swift` — entry point (`@main`)
- `Sources/App/` — состояние приложения
  - `RoutesStore.swift` — загрузка `norilsk_routes.json` из bundle
  - `LocationManager.swift` — обёртка над CLLocationManager
- `Sources/Map/MapScreen.swift` — Yandex MapKit + полилинии маршрутов
- `Sources/Routes/`
  - `RouteListSheet.swift` — список с поиском и табами Маршруты/Остановки
  - `RouteDetailScreen.swift` — детали + расписание (заглушка)
- `Sources/Stop/StopScreen.swift` — будильник у остановки (CLCircularRegion — TODO)
- `Sources/Menu/MenuScreen.swift` — экран меню
- `Sources/Favorites/FavoritesScreen.swift` — экран избранного
- `Resources/` — JSON-ассеты + `PrivacyInfo.xcprivacy`
- `Podfile` — YandexMapsMobile `4.45.0-full` (Firebase/Alamofire/SwiftyJSON — следующие этапы)
- `project.yml` — XcodeGen описание проекта

## Сборка локально (нужен Mac + Xcode)

```bash
brew install xcodegen
cd iosApp
xcodegen
pod install
open NorilskTransit.xcworkspace
```

В Xcode: Signing & Capabilities → Team → Run.

## CI (бесплатно через GitHub Actions)

Workflow `.github/workflows/ios-build.yml` запускается при push в `main` и `arena/**`,
а также вручную (Actions → iOS Build → Run workflow):
- macos-14 раннер, Xcode фиксированной версии (проверяется через `xcode-select`)
- xcodegen → pod install → xcodebuild (iOS Simulator, `generic/platform=iOS Simulator`)
- Любая ошибка `xcodebuild` роняет workflow; отсутствие `.app` считается ошибкой
- Артефакт `.app` + `build.log` доступны на странице Actions

## Что ещё нужно

1. **Yandex MapKit API key** — добавить в Secrets репозитория как `YANDEX_MAPKIT_API_KEY`.
2. **Firebase `GoogleService-Info.plist`** — после регистрации в console.firebase.google.com.
3. **Иконки iOS** — сгенерировать `AppIcon.appiconset` (1024×1024 + @2x/@3x для всех устройств).
4. **Загрузка расписания** — сейчас только заглушка в `ScheduleView`.

## Известные ограничения

- Будильник через `CLCircularRegion` + `UNUserNotificationCenter` — TODO
- Голосовое оповещение — TODO (Android использует TTS)
- Свайп-вниз для закрытия sheet'ов — TODO
- VoiceOver / Dynamic Type — не тестировалось
