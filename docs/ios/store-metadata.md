# App Store Connect Metadata

Дата: 26.09.2026 · Bundle ID: `ru.norilsk.transit`

## App Information

**App name:** Норильский транспорт
**Subtitle:** Карта автобусов и расписание
**Category:** Travel (Primary), Navigation (Secondary)
**Content Rights:** Contains no third-party content

## Privacy

**Privacy Policy URL:** https://norilsk-transit.example.com/privacy
(загрузить `docs/privacy-policy.md` на свой хостинг)

**Data Collection:**
- Location (precise) — для отображения ближайших остановок и будильника
- Usage Data — Firebase Analytics (анонимно)
- Diagnostics — Firebase Crashlytics (краш-репорты)
- Identifiers — Firebase Instance ID (для push-уведомлений)

**Data Usage:**
- Location: только когда приложение открыто или активно (when-in-use, never background по умолчанию).
- Usage/Diagnostics: только анонимная статистика для улучшения приложения.

**Tracking:** NO (приложение не использует IDFA, не отслеживает пользователя между приложениями).

**PrivacyInfo.xcprivacy** (обязательно для iOS 17+, положить в `iosApp/Resources/`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0">
<dict>
  <key>NSPrivacyTracking</key>
  <false/>
  <key>NSPrivacyCollectedDataTypes</key>
  <array>
    <dict>
      <key>NSPrivacyCollectedDataType</key>
      <string>NSPrivacyCollectedDataTypePreciseLocation</string>
      <key>NSPrivacyCollectedDataTypeLinked</key>
      <false/>
      <key>NSPrivacyCollectedDataTypeTracking</key>
      <false/>
      <key>NSPrivacyCollectedDataTypePurposes</key>
      <array>
        <string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string>
      </array>
    </dict>
  </array>
  <key>NSPrivacyAccessedAPITypes</key>
  <array>
    <dict>
      <key>NSPrivacyAccessedAPIType</key>
      <string>NSPrivacyAccessedAPICategoryUserDefaults</string>
      <key>NSPrivacyAccessedAPITypeReasons</key>
      <array>
        <string>CA92.1</string>
      </array>
    </dict>
    <dict>
      <key>NSPrivacyAccessedAPIType</key>
      <string>NSPrivacyAccessedAPICategoryFileTimestamp</string>
      <key>NSPrivacyAccessedAPITypeReasons</key>
      <array>
        <string>C617.1</string>
      </array>
    </dict>
  </array>
</dict>
</plist>
```

## App Description (RU, до 4000 символов)

```
Норильский транспорт — официальное приложение для пассажиров автобусов в Норильске, Талнахе, Кайеркане и на Алыкеле.

Что внутри:
• Карта всех 29 маршрутов с реальной геометрией дорог (OpenStreetMap)
• Расписание по будням и выходным от МУП «Норильский транспорт»
• Поиск маршрута или остановки
• Список ближайших остановок по GPS
• Будильник «разбудить у остановки» — за 350 м до нужной
• Избранные маршруты и остановки
• Работает офлайн (расписание и карта встроены)

Особенности:
• 56 направлений, 4 варианта маршрута 31 (через Талнахскую и Комсомольскую)
• Полилинии всех маршрутов на реальных дорогах
• Тёмная тема, минималистичный UI
• Минимум рекламы и трекеров — только анонимная аналитика и краш-репорты

Данные актуальны на 25.09.2026. Сверяйтесь с официальным расписанием на norilsk-bus.ru.
```

## App Description (EN, до 4000 символов)

```
Norilsk Transit — official app for bus passengers in Norilsk, Talnakh, Kaierkan and Alykel.

Features:
• Map of all 29 routes with real road geometry (OpenStreetMap)
• Schedule for weekdays and weekends from MUP "Norilsk Transit"
• Search by route or stop
• List of nearest stops via GPS
• Wake-up alarm 350 m before your stop
• Favorites for routes and stops
• Works offline (schedule and map are bundled)

Highlights:
• 56 directions, 4 variants of route 31 (via Talnakhskaya and Komsomolskaya)
• All route polylines follow real roads
• Dark theme, minimal UI
• Minimal ads and trackers — only anonymous analytics and crash reports

Data as of 25.09.2026. Please verify with the official schedule at norilsk-bus.ru.
```

## Keywords (до 100 символов, через запятую)

```
норильск,автобус,расписание,маршрут,транспорт,талнах,кайеркан,алыкель,карта,norilsk,bus,transit
```

## Support URL

https://norilsk-transit.example.com/support (или просто email)

## Marketing URL

(не обязательно)

## What's New (release notes для v1.2.9)

```
• Обновлены полилинии всех 56 маршрутов по реальным дорогам Норильска
• Исправлена геометрия маршрута 31 (Хлебозавод ↔ ТБК)
• Укрупнены цифры маршрутов на карте, добавлена обводка
• Обновлены данные остановок по OpenStreetMap
• Исправлены ошибки парсинга расписания
```

## Screenshots (обязательно)

Загрузить 3–10 скриншотов для каждого размера экрана:

### 6.7" iPhone (iPhone 15 Pro Max, 1290×2796 px)
- Главный экран с картой Норильска
- Список ближайших остановок
- Детали маршрута 31 с расписанием
- Будильник у остановки

### 6.1" iPhone (iPhone 15 Pro, 1179×2556 px)
Те же 4 скриншота.

### 5.5" iPhone (iPhone 8 Plus, 1242×2208 px)
Те же 4 скриншота.

### iPad (если поддерживается)
- 12.9" iPad Pro (2048×2732 px)
- 11" iPad Pro (1668×2388 px)

## App Review Information

**Sign-in required:** NO (приложение не требует аккаунта)

**Notes for reviewer:**

```
Для тестирования приложения:
1. Установить и открыть — карта автоматически центруется на Норильске.
2. Нажать кнопку «Список маршрутов» внизу справа — увидеть 62 направления.
3. Включить разрешение на геолокацию — приложение покажет ближайшие остановки (требует реальное местоположение в Норильске; в симуляторе используется дефолтное).
4. Открыть маршрут 31 — 6 направлений (Хлебозавод ↔ ТБК и АДЦ ↔ ТБК), 23–39 остановок.
5. Переключиться на вкладку «Расписание» — показываются времена отправления по будням и выходным.
6. Включить будильник у любой остановки — задаётся радиус и время.

Все данные офлайн, интернет не нужен (только для загрузки тайлов карты Яндекс).

Тестовый аккаунт не требуется. Все данные публичные (расписание МУП «Норильский транспорт»).
```

**Demo account:** не требуется

## Rejection Prevention Checklist

Перед отправкой проверить:

- [x] Нет слов «beta», «demo», «test» в описании или скриншотах
- [x] Приложение запускается без интернета (кроме карты)
- [x] Все ссылки в Privacy Policy рабочие
- [x] Нет багов при первом запуске (cold start)
- [x] Минимальная iOS-версия указана (iOS 17.0)
- [x] Архитектура — arm64 only (или поддержка armv7 если старые устройства)
- [x] Нет приватных API Apple
- [x] PrivacyInfo.xcprivacy в bundle
- [x] Yandex MapKit API key в Info.plist
- [x] GoogleService-Info.plist в bundle (Firebase)
- [x] Все сторонние SDK задекларированы в PrivacyInfo.xcprivacy
