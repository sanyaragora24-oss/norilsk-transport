# Norilsk Transit

Официальное приложение для пассажиров автобусов в Норильске, Талнахе, Кайеркане и на Алыкеле.

- **Карта всех 29 маршрутов** с реальной геометрией дорог (OpenStreetMap)
- **Расписание** по будням и выходным от МУП «Норильский транспорт»
- **Будильник** «разбудить у остановки» — за 350 м до нужной
- **Избранные** маршруты и остановки
- Работает **офлайн** (расписание и карта встроены)

## Платформы

| Платформа | Статус | Версия |
|---|---|---|
| Android | ✅ публикуется в Google Play / RuStore | 1.2.6 (versionCode 8) |
| iOS | 🚧 в разработке, SwiftUI каркас | планируется 1.2.6 |

## Структура репозитория

```
norilsk-transport/
├── app/                       # Android-приложение (Kotlin + Compose)
│   └── src/main/assets/
│       ├── norilsk_routes.json    # 56 маршрутов, ~1.5 МБ
│       └── norilsk_schedule.json  # расписания
├── iosApp/                    # iOS-приложение (SwiftUI)
│   ├── Sources/
│   │   ├── App/                  # RoutesStore, LocationManager
│   │   ├── Map/                  # MapScreen
│   │   ├── Routes/               # RouteListSheet, RouteDetailScreen
│   │   └── Stop/                 # StopScreen
│   ├── Resources/
│   │   ├── norilsk_routes.json
│   │   ├── norilsk_schedule.json
│   │   └── PrivacyInfo.xcprivacy
│   ├── Podfile                # YandexMapsMobile + Firebase + Alamofire
│   └── project.yml            # XcodeGen
├── docs/
│   ├── ios/
│   │   ├── build-instructions.md    # Пошаговая инструкция для Mac
│   │   └── store-metadata.md        # App Store Connect данные
│   └── privacy-policy.md
└── .github/
    └── workflows/
        ├── ios-build.yml         # macOS + Xcode, бесплатно
        └── android-build.yml     # ubuntu + JDK 21
```

## Сборка

### Android

```bash
./gradlew :app:assembleDebug   # APK для отладки
./gradlew :app:assembleRelease # подписанный APK
```

Нужен `local.properties` с `MAPKIT_API_KEY` и Firebase `google-services.json`.

### iOS — через GitHub Actions (бесплатно)

CI запускается автоматически при push в `main` или через вкладку Actions → iOS Build → Run workflow.

Что проверяется:
- ✅ Xcode проект генерируется через xcodegen
- ✅ CocoaPods устанавливаются (YandexMapsMobile, Firebase, Alamofire)
- ✅ Swift компилируется против iOS 17 SDK
- ✅ JSON-ассеты валидны
- 📦 .app bundle загружается как artifact (можно скачать и потестить в симуляторе)

**Бесплатно** для публичных репозиториев. macos-14 runner = Apple Silicon, быстрее Intel.

### iOS — локально на Mac

```bash
brew install xcodegen
cd iosApp
xcodegen
pod install
open NorilskTransit.xcworkspace
# В Xcode: Signing & Capabilities → Team → Run
```

Подробнее: [`docs/ios/build-instructions.md`](docs/ios/build-instructions.md).

## Деплой

### Android

- Google Play Console: загрузить `app/build/outputs/bundle/release/app-release.aab`.
- RuStore: загрузить `app/build/outputs/apk/release/app-release.apk`.
- Play App Signing — обязательно, иначе потеряем ключ.

### iOS

- TestFlight (бесплатно, $99/год нужен только для App Store): `xcodebuild ... archive` → Xcode Organizer → Distribute.
- App Store: после модерации Apple (24–48 часов обычно).

## Данные маршрутов

`assets/norilsk_routes.json`:
- 54 направления (включая 4 направления 31: Хлебозавод ↔ ТБК)
- Полилинии по реальным дорогам OSM
- OSRM (`router.project-osrm.org`) для генерации геометрии

`assets/norilsk_schedule.json`:
- 62 направления (включая старые неиспользуемые)
- Расписание по будням и выходным от МУП «Норильский транспорт»

## Лицензия

Приложение принадлежит владельцу, без открытого исходного кода.

Данные маршрутов и расписаний: © МУП «Норильский транспорт», используются в ознакомительных целях.
