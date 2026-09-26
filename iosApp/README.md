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
- `Resources/` — JSON-ассеты + `PrivacyInfo.xcprivacy`
- `Podfile` — YandexMapsMobile, Firebase, Alamofire, SwiftyJSON
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

Workflow `.github/workflows/ios-build.yml` запускается автоматически при push в main:
- macos-14 раннер
- xcodegen → pod install → xcodebuild
- Артефакт `.app` доступен на странице Actions

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
