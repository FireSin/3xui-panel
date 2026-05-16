# 3xui Panel

Android-приложение для удалённого администрирования серверов [3x-ui](https://github.com/MHSanaei/3x-ui). Без собственного бэкенда — приложение ходит в API панелей напрямую, креды и кэш хранятся локально в зашифрованной SQLCipher-БД.

## Возможности

- **Мультипанель** — несколько серверов 3x-ui в одном приложении, быстрое переключение между ними.
- **Dashboard** — статус Xray, CPU/RAM/сеть/uptime, история метрик (CPU, MEM, Net In/Out, Online) на 6 часов с сеткой значений и подписями оси Y.
- **Inbounds** — список / добавление / редактирование / удаление / включение-выключение. Поддерживаются 10 протоколов: VMess, VLESS, Trojan, Shadowsocks, WireGuard, Hysteria (v2), Mixed, HTTP, Tunnel, TUN. Развёртывание инбаунда на конкретной ноде при наличии multi-node.
- **Clients** — CRUD по клиентам всех протоколов, сброс трафика, истечение по сроку/объёму, lastOnline-метка, копирование клиентов из другого инбаунда, импорт инбаунда из JSON.
- **Share** — QR-код и подписочная ссылка для конечного пользователя (server-rendered с локальным fallback под Trojan/Hysteria).
- **Stats** — графики трафика по клиентам и инбаундам (Vico), тап по столбику показывает числовое значение.
- **Nodes** — управление multi-node setup-ом: список, добавление, тест подключения, метрики каждой ноды.
- **Panel settings** — настройка подписочного URL, Telegram-бота, web-параметров, 2FA; смена логина-пароля; рестарт панели.
- **API tokens** — CRUD-токены панели; при добавлении новой панели токен авто-привязывается.
- **Custom geo** — управление кастомными geo-источниками, скачивание/обновление.
- **System actions** — сброс всего трафика, обновление панели, установка Xray-версии, бэкап в Telegram-бот.
- **Auto-backup** — периодический бэкап `x-ui.db` всех панелей в выбранную SAF-папку (Daily/Weekly/manual, retention 7 файлов на панель).
- **2FA** — OTP при добавлении/редактировании панели.
- **WebSocket realtime** — пуш-апдейты статуса/notifications/инвалидация кэша inbounds/clients от панели (если cookie-сессия активна).
- **In-app updates** — кнопка «Проверить обновление» в Settings → About: ходит в GitHub Releases API, скачивает APK с прогрессом, открывает системный установщик.
- **App lock** — PIN/биометрия при запуске.

## Архитектура

```
Android app  ──HTTPS──▶  3x-ui panel(s)
```

Без бэкенда. Под каждую панель — свой `OkHttpClient` с per-panel `CookieJar` и `TrustManager` (опционально self-signed). Сессии кэшируются in-memory (`ConcurrentHashMap`, TTL 50 мин), Bearer-токен на `/panel/api/*` где доступен, cookie+CSRF на `/panel/setting/*` и `/panel/xray/*`.

Подробнее: [docs/architecture.md](docs/architecture.md).

### Модули

```
3xui-panel/
├── app/                                           # MainActivity, NavGraph, ThemeRoot, WsToastHost
├── core/
│   ├── common/    Result/DomainError, ApkInstaller, WsUiEventBus, утилиты coroutines
│   ├── crypto/    Keystore helpers, passphrase wrap/unwrap (AES-256-GCM)
│   ├── data/      Room+SQLCipher, DataStore, repositories, AppUpdate (GitHub Releases)
│   ├── designsystem/  Theme, Typography, общие Composable, HistoryLineChart
│   ├── network/   OkHttp, Retrofit, CookieJar, TrustManagers, SPKI pinning
│   ├── sampler/   TrafficSampler — фоновое снятие трафика по клиентам
│   └── xui/       3x-ui API (XuiApi), XuiClient (фасад), DTO+mapping, WebSocket
└── feature/
    ├── panels/      список панелей, add/edit/delete, активная панель
    ├── dashboard/   статус Xray + история метрик
    ├── inbounds/    CRUD inbounds (10 протоколов), copyClients, импорт JSON
    ├── clients/     CRUD clients всех протоколов, traffic stats
    ├── share/       QR + sub-link (server-side getClientLinks)
    ├── stats/       графики трафика клиентов/инбаундов (Vico)
    ├── nodes/       multi-node management + per-node history
    ├── settings/    panel-setup, api-tokens, geo, system actions, auto-backup, about, update
    └── lock/        PIN/биометрия при запуске
```

`app` → `feature:*` → `core:*`. Между `feature:*` нет прямых зависимостей.

## Стек

- Kotlin 2.1.20, AGP 8.13.2, Gradle 9.3.1, JDK 21
- Jetpack Compose Material 3 (`compose-bom 2025.04.01`)
- Hilt 2.56, Jetpack Navigation, WorkManager
- Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization 1.7.3
- Room 2.7 + SQLCipher 4.5
- Vico 2.1 (графики), Android Keystore (StrongBox/TEE)
- minSdk 26, targetSdk 35

## Сборка

```bash
./gradlew :app:assembleDebug   # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease # → app/build/outputs/apk/release/app-release.apk (только если подпись настроена)
```

Android SDK подтягивается через `local.properties` (`sdk.dir=...`). Тесты: `./gradlew test`.

### Подпись release-сборки

Положи в `~/.gradle/gradle.properties` (или передай через env `ORG_GRADLE_PROJECT_*`):

```
SIGNING_STORE_FILE=/path/to/release.jks
SIGNING_STORE_PASSWORD=...
SIGNING_KEY_ALIAS=release
SIGNING_KEY_PASSWORD=...
```

Если переменные не заданы — `assembleRelease` собирает неподписанный APK (Android его не установит).

## CI/CD

- `.github/workflows/android-ci.yml` — на PR и push в любую ветку кроме `main`: `assembleDebug` + lint + unit-тесты.
- `.github/workflows/android-release.yml` — на push в `main`: подписанный release-APK как artifact (30 дней). На тэг `v*`: GitHub Release с APK.

Ветка `main` защищена ruleset-ом: прямой push запрещён, merge только через PR с зелёным CI-чеком `build`. Разработка идёт в `dev`, PR `dev → main`.

### Релиз

```bash
# на dev
# bump versionCode/versionName в app/build.gradle.kts
git push   # CI на PR должен пройти
gh pr create --base main --head dev --title "Release vX.Y.Z"
# merge через UI после зелёного CI

git checkout main && git pull
git tag vX.Y.Z
git push --tags   # release workflow подпишет APK и опубликует в GitHub Releases
```

После публикации релиза в приложении кнопка «Проверить обновление» (Settings → About) увидит его и предложит установить.

## Документы

- [docs/brief.md](docs/brief.md) — изначальный бриф и состав MVP (исторический).
- [docs/architecture.md](docs/architecture.md) — архитектура.
