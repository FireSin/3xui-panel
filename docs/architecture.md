# Архитектура: 3xui Panel

Android-приложение для удалённого управления панелями 3x-ui. Без собственного бэкенда — приложение ходит в API панелей напрямую. Данные (включая креды) хранятся локально в шифрованной БД.

## 1. Компоненты и потоки

```
┌───────────────────────────┐                 ┌────────────────────┐
│      Android device       │                 │    3x-ui panels    │
│                           │                 │  (remote servers)  │
│  ┌─────────────────────┐  │   HTTPS         │                    │
│  │  UI (Compose)       │  │   (form login,  │  ┌──────────────┐  │
│  │     :feature:*      │  │   cookie-based  │  │ /login       │  │
│  └──────────┬──────────┘  │   session)      │  │ /panel/api/* │  │
│             │             │                 │  │ /server/*    │  │
│  ┌──────────▼──────────┐  │                 │  │ /xui/API/*   │  │
│  │  Repositories       │  │                 │  └──────────────┘  │
│  │     :core:data      │◀─┼─────────────────┼─▶                  │
│  └──────┬────────┬─────┘  │                 │                    │
│         │        │        │                 └────────────────────┘
│  ┌──────▼──┐  ┌──▼──────┐ │
│  │ Room +  │  │ Retrofit│ │
│  │SQLCipher│  │ + OkHttp│ │
│  └────┬────┘  └─────────┘ │
│       │                   │
│  ┌────▼───────────────┐   │
│  │  Android Keystore  │   │
│  │  (AES-256-GCM,     │   │
│  │   non-exportable)  │   │
│  └────────────────────┘   │
└───────────────────────────┘
```

- UI-слой на Compose, разложен по feature-модулям.
- Репозитории в `:core:data` скрывают источник данных (Room или Retrofit).
- Для каждой панели — отдельный `OkHttpClient` со своим `CookieJar` и `TrustManager` (self-signed опция).
- Вся БД шифруется SQLCipher. Passphrase БД защищён Keystore-ключом.

## 2. Модули Gradle

```
3xui-panel/
├── app/                        # MainActivity, корневой NavGraph, ThemeRoot
├── core/
│   ├── common/                 # Result/DomainError, coroutines utils
│   ├── designsystem/           # Theme, Typography, общие Composable
│   ├── data/                   # Room, SQLCipher, DataStore, Repository
│   ├── network/                # OkHttp, Retrofit, CookieJar, TrustManagers
│   ├── crypto/                 # Keystore helpers, passphrase wrap/unwrap
│   └── xui/                    # API-интерфейсы 3x-ui, маппинг DTO, XuiClient
└── feature/
    ├── panels/                 # список панелей, add/edit/delete, выбор активной
    ├── dashboard/              # статус Xray, CPU/RAM/сеть
    ├── inbounds/               # CRUD инбаундов, toggle enable
    ├── clients/                # CRUD клиентов, сброс трафика
    ├── share/                  # QR + подписочная ссылка клиента
    └── stats/                  # графики трафика (Vico)
```

- `app` зависит от всех `feature:*`. `feature:*` зависят от `core:*`. Между `feature:*` прямых зависимостей нет — общие абстракции поднимаются в `core`.
- DI через Hilt: `@HiltAndroidApp` в `:app`, по Hilt-модулю на каждый `core`-модуль, `@HiltViewModel` в feature-ах.

## 3. Локальная БД (Room + SQLCipher)

БД — один файл, зашифрованный SQLCipher. Открывается через `SupportFactory(passphrase)` из `net.zetetic:android-database-sqlcipher`.

### Таблицы

**panels** — подключённые пользователем панели 3x-ui.

| Поле | Тип | Комментарий |
|---|---|---|
| `id` | TEXT PK | UUID v4 |
| `name` | TEXT NOT NULL | Имя, задаваемое пользователем |
| `base_url` | TEXT NOT NULL | `https://panel.example.com:2053` |
| `login` | TEXT NOT NULL | Хранится в открытом виде внутри зашифрованной БД |
| `password` | TEXT NOT NULL | Аналогично |
| `trust_self_signed` | INTEGER NOT NULL | 0/1 |
| `is_active` | INTEGER NOT NULL | 0/1 — текущая выбранная панель (при первом добавлении — 1) |
| `created_at` | INTEGER NOT NULL | epoch millis |
| `last_login_at` | INTEGER | epoch millis, NULL если ни разу не логинились |

Constraint: максимум одна строка с `is_active = 1` — ensure-ится в репозитории транзакционно.

**panel_session** — закэшированная сессия панели (cookie). Опционально, можно и в памяти держать. Если в БД — переживает рестарт процесса.

| Поле | Тип |
|---|---|
| `panel_id` | TEXT PK, FK → panels.id ON DELETE CASCADE |
| `cookie_value` | TEXT NOT NULL |
| `issued_at` | INTEGER NOT NULL |
| `expires_at` | INTEGER NOT NULL |

**Решение MVP:** держим сессии только в памяти (`ConcurrentHashMap<PanelId, Session>`). При холодном старте — реже re-login, это приемлемо. Таблицу `panel_session` оставляем в бэклоге.

**audit_log** — локальный журнал действий для диагностики.

| Поле | Тип |
|---|---|
| `id` | INTEGER PK AUTOINCREMENT |
| `panel_id` | TEXT NULL, FK → panels.id ON DELETE SET NULL |
| `action` | TEXT NOT NULL (`PANEL_ADD`, `LOGIN`, `INBOUND_CREATE`, …) |
| `outcome` | TEXT NOT NULL (`OK`, `FAIL`) |
| `message` | TEXT NULL |
| `created_at` | INTEGER NOT NULL |

UI истории для MVP нет, но таблица пополняется — полезно при отладке.

### Миграции

Room Migrations, versioned. Первая миграция ставит схему выше. Ломаные миграции — строго запрещены, на каждую смену схемы — `Migration(from, to)`.

## 4. Шифрование

### Passphrase БД

- При первом запуске генерируется 32-байтная passphrase: `SecureRandom.getInstanceStrong().nextBytes(32)`.
- Android Keystore хранит AES-256-GCM ключ с алиасом `db_passphrase_wrap_v1`:
  - `KeyGenParameterSpec.Builder(alias, PURPOSE_ENCRYPT | PURPOSE_DECRYPT)`
  - `.setBlockModes(BLOCK_MODE_GCM)`
  - `.setEncryptionPaddings(ENCRYPTION_PADDING_NONE)`
  - `.setKeySize(256)`
  - `.setIsStrongBoxBacked(true)` с fallback на TEE при отсутствии StrongBox.
  - `.setUserAuthenticationRequired(false)` — иначе потребуется биометрия при каждом старте. BiometricPrompt на открытие БД — в бэклоге.
- Wrapped passphrase (IV ‖ ciphertext ‖ tag) сохраняется в `EncryptedSharedPreferences`? Нет — в обычном файле `passphrase.bin` в `filesDir`. Смысл: Keystore-ключ и так non-exportable, дополнительное шифрование prefs поверх избыточно. При старте: читаем файл, расшифровываем Keystore-ключом, получаем passphrase, передаём в `SupportFactory`.

`:core:crypto` предоставляет интерфейс:
```kotlin
interface DbPassphraseProvider {
    suspend fun obtain(): CharArray  // создаёт при первом запуске, unwrap-ит иначе
    suspend fun rotate()             // бэклог: re-encrypt БД новым passphrase
}
```

### Креды панелей

Отдельно не шифруются — лежат в зашифрованной БД. Причина: двойное шифрование не повышает устойчивость к атакам (root, physical, cold boot), но усложняет код и миграции. Единый контур защиты — Keystore → passphrase → SQLCipher → данные.

### Что защищено и от чего

| Угроза | Защита | Замечание |
|---|---|---|
| Украли залоченный телефон | Keystore-ключ недоступен без разблокировки, passphrase не восстановить | OK |
| Украли разлоченный телефон | Приложение доступно, данные видны | Лечится PIN/биометрией в бэклоге |
| Root + вытащили `filesDir` | Wrapped passphrase + DB бесполезны без Keystore-ключа | OK (TEE/StrongBox непроницаем) |
| Root + `Frida` / live app | Всё видно | Не защищаем, out of scope |
| Бэкап устройства (ADB/Google) | `android:allowBackup="false"` и `android:dataExtractionRules` — запрещаем бэкап всего приложения | См. Manifest |

## 5. Работа с 3x-ui API

3x-ui — Go-сервер на gin. `/login` принимает `application/x-www-form-urlencoded` с полями `username` и `password`, выставляет cookie-сессию `3x-ui`. Все последующие запросы идут с этой cookie. Сессия живёт ~1 час по умолчанию.

Источники: [api.go](https://github.com/MHSanaei/3x-ui/blob/main/web/controller/api.go), [Postman-коллекция](https://documenter.getpostman.com/view/16802678/2s9YkgD5jm), [ref-клиент](https://github.com/iamhelitha/3xui-api-client).

### Структура сетевого слоя

- `:core:network` создаёт `OkHttpClient` **по одному на каждую панель**. Ключевые настройки:
  - свой `CookieJar` (in-memory, per-panel);
  - `HostnameVerifier` и `X509TrustManager` — дефолтный или `no-op` при `trust_self_signed = true`;
  - connect/read timeouts — 10/20 сек;
  - `HttpLoggingInterceptor` (уровень HEADERS в debug, NONE в release).
- `OkHttpClientFactory` кэширует инстансы по `panelId` (`ConcurrentHashMap`), пересоздаёт при смене URL/trust-флага.
- `:core:xui.XuiClient` — высокоуровневый фасад: `login()`, `listInbounds()`, `addClient(...)`, `reset(...)`. Внутри — Retrofit-сервисы, авто-релогин при 401.

### Алгоритм сессии

1. `XuiClient.request(panelId, call)` берёт `XuiSession` из `XuiSessionCache` (in-memory, `ConcurrentHashMap<PanelId, Session>` с TTL 50 мин).
2. Нет сессии → `POST /login` (form-encoded), извлекаем cookie `3x-ui` из `Set-Cookie`, кладём в CookieJar и в кэш.
3. Выполняем основной запрос. Если `401` или HTML-редирект на `/login` — инвалидируем сессию, логинимся повторно, повторяем запрос один раз.
4. Если опять `401` — `XuiAuthException`, пользователю предлагается проверить креды.

### Используемые эндпоинты

Базовый путь админских API: `/panel/api/inbounds`.

| Функция | Метод и путь | Комментарий |
|---|---|---|
| Логин | `POST /login` (form) | `Set-Cookie: 3x-ui=...` |
| Список инбаундов | `GET /panel/api/inbounds/list` | Включает и инбаунды, и их клиентов |
| Инбаунд по id | `GET /panel/api/inbounds/get/:id` | |
| Создать инбаунд | `POST /panel/api/inbounds/add` | JSON |
| Обновить инбаунд | `POST /panel/api/inbounds/update/:id` | |
| Удалить инбаунд | `POST /panel/api/inbounds/del/:id` | |
| Добавить клиента | `POST /panel/api/inbounds/addClient` | `{id, settings: "<json-string>"}` |
| Обновить клиента | `POST /panel/api/inbounds/updateClient/:clientId` | `clientId` = UUID клиента |
| Удалить клиента | `POST /panel/api/inbounds/:id/delClient/:clientId` | |
| Сбросить трафик клиента | `POST /panel/api/inbounds/:id/resetClientTraffic/:email` | |
| Трафик клиента по email | `GET /panel/api/inbounds/getClientTraffics/:email` | |
| Трафик клиента по id | `GET /panel/api/inbounds/getClientTrafficsById/:id` | |
| Статус сервера | `POST /server/status` | CPU/RAM/сеть/uptime |
| Онлайн-клиенты | `POST /panel/inbound/onlines` | Для дашборда |

### DTO и маппинг

Для каждого эндпоинта — `@Serializable` DTO в `:core:xui.dto`. Доменные модели в `:core:xui.model`. Конверсия — extension-функции `XxxDto.toDomain()`. Это изолирует доменный слой от эволюции API панели.

### Ретраи и таймауты

- GET: до 2 повторов с экспоненциальным backoff (250 мс → 1 c) на IOException/5xx.
- POST: без автоматических ретраев (кроме единственного re-login на 401).
- Глобальные таймауты: connect 10 с, read 20 с. Для `/server/status` читаем с шагом — чаще дёргать не стоит.

## 6. UI-слой и навигация

- Корневой `NavGraph`: стартовый экран зависит от состояния БД. Нет панелей → `PanelsEmptyScreen` (кнопка «Добавить панель»). Есть активная — `DashboardScreen`.
- Нижняя навигация активной панели: `Dashboard`, `Inbounds`, `Clients`, `Stats`.
- Экран переключения / управления панелями — отдельный stack, вызывается из `TopAppBar`.
- Архитектура экрана: Compose + ViewModel + `StateFlow<UiState>`. MVI-lite. Intents как методы VM.
- `UiState` = `Loading | Content(data) | Error(DomainError)`. В `Error` — retry action.

## 7. Обработка ошибок

Единый `DomainError` sealed class в `:core:common`:

```kotlin
sealed class DomainError {
    data class Network(val cause: Throwable) : DomainError()      // нет сети, таймаут
    data class Tls(val message: String) : DomainError()           // сертификат
    data object InvalidCredentials : DomainError()                // 401 после re-login
    data class PanelUnreachable(val httpCode: Int?) : DomainError()
    data class PanelResponse(val code: Int, val body: String) : DomainError()
    data class Unexpected(val cause: Throwable) : DomainError()
}
```

Репозитории возвращают `Result<T, DomainError>` (самописный, не `kotlin.Result`). Маппинг HTTP → DomainError в `:core:xui.ErrorMapper`. UI показывает специфический snackbar/диалог по типу ошибки. Trace идёт в `audit_log` с `outcome=FAIL`.

## 8. TLS и self-signed

- По умолчанию — строгая проверка через дефолтный `X509TrustManager`.
- Чекбокс «доверять самоподписанному сертификату» в форме добавления панели. При включении — `trust_self_signed = true` в БД.
- При `true` — кастомный `TrustManager` без проверки, но обязательно:
  - красная плашка-предупреждение на экране добавления/редактирования;
  - иконка `shield_off` на карточке панели в списке;
  - в dashboard первая строка — «Подключение без проверки сертификата» пока этот режим активен.
- HTTP (plain) — не разрешаем. `base_url` проходит валидацию на `https://`.

## 9. Прочие соображения безопасности

- `android:allowBackup="false"` и `android:dataExtractionRules` в Manifest — чтобы ADB-backup и Google-бэкап не утащили БД.
- `FLAG_SECURE` на Activity — запрещает скриншоты и отображение в recents. На экранах, показывающих креды/QR — обязательно. На списке панелей — по желанию пользователя (настройка).
- `android:debuggable="false"` в release (Gradle делает сам).
- R8/ProGuard в release — обязательно, с rules для Retrofit/kotlinx.serialization/Room/SQLCipher.
- Логи: никаких кредов и cookie в `HttpLoggingInterceptor` в release (`NONE`). В debug — уровень `HEADERS`, body не логируется.
- `networkSecurityConfig` — `cleartextTrafficPermitted=false` глобально. Для `trust_self_signed` — кастомный TrustManager на уровне OkHttp, не через xml-конфиг (чтобы не ослаблять всё приложение).

## 10. Сборка и CI/CD

### Gradle

- `gradle/libs.versions.toml` (version catalog) для управления версиями.
- Модули: `app` — `com.android.application`, остальные — `com.android.library` + `kotlin-android`.
- `buildTypes`: `debug` (applicationIdSuffix `.debug`), `release` (R8, shrinkResources).
- Подпись релиза через секреты, см. ниже.
- `buildFeatures.compose = true`, Compose Compiler 2.x.
- JDK toolchain: 21 (AGP 8.x + Compose Compiler — Java 25 не поддерживается).

### GitHub Actions

**`.github/workflows/android-release.yml`**
- Триггер: push в `main` и git-теги `v*`.
- Шаги:
  1. `actions/checkout@v4` с `fetch-depth: 0`.
  2. `actions/setup-java@v4`: `temurin`, `21`.
  3. `gradle/actions/setup-gradle@v4`.
  4. Восстановить keystore: `echo "$SIGNING_KEYSTORE_BASE64" | base64 -d > $RUNNER_TEMP/release.jks`.
  5. Экспорт переменных окружения: `ORG_GRADLE_PROJECT_SIGNING_STORE_FILE`, `..._STORE_PASSWORD`, `..._KEY_ALIAS`, `..._KEY_PASSWORD` — Gradle их подхватывает через `providers.gradleProperty(...)`.
  6. `./gradlew :app:assembleRelease`.
  7. На теге `v*` — `softprops/action-gh-release@v2` публикует `app-release.apk` в GitHub Releases.
- Артефакт сборки (`actions/upload-artifact`) — для push в `main` без тега, чтобы можно было скачать APK из run-а.

**`.github/workflows/android-ci.yml`**
- Триггер: PR и push в не-main ветки.
- `./gradlew ktlintCheck detekt :app:lintDebug :app:testDebugUnitTest`.
- Кэш Gradle.

### Секреты (уже созданы)

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_KEY_ALIAS`

Подпись настраивается в `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(providers.gradleProperty("SIGNING_STORE_FILE").get())
            storePassword = providers.gradleProperty("SIGNING_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("SIGNING_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("SIGNING_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release") }
    }
}
```

## 11. Зависимости (обоснование новых)

- `net.zetetic:android-database-sqlcipher` — шифрование БД. Единственный зрелый выбор для SQLite на Android.
- `androidx.security:security-crypto` — `MasterKey`, если будем использовать `EncryptedSharedPreferences` где-то. В текущей схеме passphrase лежит в обычном файле, расшифровываемом Keystore-ключом напрямую — можно обойтись без этой зависимости. **Решение:** не подключаем, пока не потребуется.
- `com.patrykandpatrick.vico:compose-m3` — графики для `:feature:stats`. Compose-native.
- `com.journeyapps:zxing-android-embedded` — генерация QR и отображение в UI.
- `androidx.work:work-runtime-ktx` — WorkManager для фонового поллинга (бэклог, но заложим каркас).

## 12. Трейд-оффы и риски

- **Потеря данных при переустановке.** Явный трейд-офф за отказ от бэкенда. Компенсация — экспорт/импорт в бэклоге.
- **Push-уведомления — best-effort.** Без серверной стороны нет настоящих push. Внутренний поллинг ограничен Doze-режимом Android. Для надёжных алертов потребуется сервер — отдельный проект в будущем.
- **Сессии 3x-ui только in-memory.** При холодном старте — массовый re-login. Приемлемо: логин быстрый, один HTTP-запрос.
- **`trust_self_signed`.** Опасная опция, обоснована реалиями 3x-ui-деплоев (часто за Nginx без сертификата/с самоподписанным). Компенсируется явными предупреждениями в UI.
- **Связь с панелью из приложения напрямую.** Если API 3x-ui изменится, выкатывать фикс нужно через новый APK. С бэкендом можно было бы изолировать. В обмен — отсутствие инфраструктуры.
- **SQLCipher увеличивает APK на ~5 МБ** (нативные библиотеки для 4 ABI) — приемлемо. Можно поджать через `abiFilters` и разделение по ABI в Gradle, если захочется.

## 13. Что осталось открытым

- Поведение при оффлайне (read-only кэш последних данных) — не решено, отложено на имплементацию `:feature:*`.
- Структура экспорт-файла в бэклоге (JSON + PBKDF2 + AES-GCM? архив формата?) — спроектируем перед реализацией.
- Будет ли экран настроек с PIN-блокировкой и FLAG_SECURE — решим после MVP.
