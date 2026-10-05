# iOS Build Instructions

Дата: 26.09.2026 · Проект: Norilsk Transit (Android v1.2.9 → iOS v1.2.9)

## ⚠️ Hard requirement: macOS + Xcode

Собрать `.ipa` для iOS **невозможно** на Windows/Linux. Apple требует:
- **macOS 13+** (Ventura или новее)
- **Xcode 16+** с iOS 17+ SDK
- **Active Apple Developer account** ($99/год) для подписи и публикации

## Что уже подготовлено на Windows

В `iosApp/` лежит каркас проекта:
- `NorilskTransitApp.swift` — SwiftUI App entry point
- `Sources/Map/MapScreen.swift` — карта Яндекс с маршрутами
- `Sources/Routes/RouteListSheet.swift`, `RouteDetailScreen.swift` — список и детали
- `Sources/Stop/StopScreen.swift` — экран остановки с будильником
- `Sources/App/RoutesStore.swift`, `LocationManager.swift` — модели и состояние
- `Podfile` — YandexMapsMobile, Firebase, Alamofire
- `project.yml` — XcodeGen описание проекта
- `Resources/norilsk_routes.json`, `norilsk_schedule.json` — данные маршрутов

Это НЕ скомпилированный проект. Только текст Swift + конфиги.

## Шаг 1: Подготовка Mac

### Вариант A — купить Mac mini M4 (~150 000 ₽)

- Mac mini M4 (16 ГБ RAM, 256 ГБ SSD) — для разработки хватит.
- macOS Sequoia 15.0+.
- Установить Xcode 16 через App Store.

### Вариант B — арендовать Mac в облаке

- **Macincloud** (https://www.macincloud.com) — от $20/мес.
- **Scaleway Bare Metal M1** — €11.99/мес.
- **GitHub Actions macOS runner** — бесплатно для публичных репозиториев.

## Шаг 2: Сгенерировать Xcode-проект

```bash
brew install xcodegen
cd iosApp
xcodegen
open NorilskTransit.xcworkspace
```

В Xcode:
1. **Signing & Capabilities** → выбрать Team (Apple Developer ID).
2. **Build Settings** → задать `DEVELOPMENT_TEAM`.
3. **Info.plist** — заполнить `YANDEX_MAPKIT_API_KEY` (получить на https://developer.tech.yandex.ru/).

## Шаг 3: Установить pods

```bash
cd iosApp
pod install
```

Зависимости:
- YandexMapsMobile 4.5+ (iOS SDK Яндекс.Карт)
- Firebase (Crashlytics + Messaging + Analytics)
- Alamofire 5.9
- SwiftyJSON 5

## Шаг 4: Подключить Firebase

1. Создать проект на https://console.firebase.google.com/
2. Добавить iOS-приложение с bundle ID `ru.norilsk.transit`.
3. Скачать `GoogleService-Info.plist` и положить в `iosApp/Resources/`.
4. В Xcode: добавить файл в проект (target → Build Phases → Copy Bundle Resources).

## Шаг 5: Запуск на симуляторе

```bash
# Из Xcode: Product → Run (⌘R)
# Или из терминала:
xcodebuild -workspace NorilskTransit.xcworkspace -scheme NorilskTransit \
  -configuration Debug -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 15'
```

Прогнать критичные сценарии:
1. Открытие карты (должна показать Норильск)
2. Список маршрутов (должно быть 62 направления)
3. Маршрут 31 (6 направлений) и 31Э (6 направлений, треки общие с 31)
4. Детали маршрута → Расписание
5. Остановка → будильник (запросить разрешения на геолокацию)

## Шаг 6: Тест на реальном iPhone

1. Подключить iPhone по USB.
2. В Xcode выбрать устройство.
3. Signing → выбрать Team.
4. Нажать Run.

Для тестового деплоя на свой телефон Apple ID должен быть в **Signing → Team**.
Если аккаунт бесплатный (без Developer Program), iPhone надо добавить в список устройств вручную.

## Шаг 7: Архив для App Store

```bash
xcodebuild -workspace NorilskTransit.xcworkspace -scheme NorilskTransit \
  -configuration Release -sdk iphoneos \
  -archivePath build/NorilskTransit.xcarchive archive
```

Открыть Xcode → Window → Organizer → Archives → Distribute App → App Store Connect → Upload.

## Шаг 8: App Store Connect

1. https://appstoreconnect.apple.com/ → My Apps → + → New App.
2. Bundle ID: `ru.norilsk.transit`
3. SKU: `norilsk-transit-2026`
4. Заполнить метаданные (см. `store-metadata.md`).
5. Privacy policy: загрузить из `docs/privacy-policy.md` на свой хостинг, дать URL.
6. Screenshots: 6.5" iPhone (1242×2688), 5.5" iPhone (1242×2208), iPad если нужно.
7. Build → выбрать архив → Submit for Review.

## Шаг 9: Модерация Apple

- Обычно 24–48 часов, но может быть до 7 дней.
- Если отклонят — будет email с причинами.

## Известные ограничения каркаса

Что **реализовано** в каркасе:
- ✅ SwiftUI структура
- ✅ Загрузка маршрутов из JSON
- ✅ Карта Яндекс с полилиниями
- ✅ Список и детали маршрутов
- ✅ Базовый будильник у остановки
- ✅ Геолокация
- ✅ Тёмная тема

Что **нужно доделать** на Mac:
- ⚠️ Расписание (загрузка из norilsk_schedule.json, переключатель будни/выходные)
- ⚠️ Избранное (FavoritesStore уже есть, но без UI)
- ⚠️ Полноценный foreground GPS-будильник (CLCircularRegion + UNUserNotificationCenter)
- ⚠️ Свайп-вниз для закрытия sheet'ов
- ⚠️ VoiceOver-тест
- ⚠️ PrivacyInfo.xcprivacy для iOS 17+ (обязателен)

## Что можно улучшить

- **KMP shared модуль** — вынести общую логику (RoutePlanner, ScheduleEstimator) в Kotlin shared и вызывать из Swift через Kotlin/Native. Уменьшит дублирование кода.
- **Реальный GPS от МУП** — добавить WebSocket-клиент для обновления позиций автобусов.
- **Оффлайн-режим** — кэшировать маршруты и расписание через CoreData.

## Сроки

- Генерация Xcode-проекта и первая сборка: **2–3 часа**
- Подключение Firebase + MapKit: **1–2 дня**
- Доделка расписания и будильника: **3–5 дней**
- Тест на устройстве + правки: **3–5 дней**
- App Store submission + модерация: **3–7 дней**

**Итого до публикации: 2–3 недели full-time.**
