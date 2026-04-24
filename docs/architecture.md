# Архитектура 3xui-panel

Документ описывает архитектуру мобильного Android-клиента и бэкенда для удалённого управления 3x-ui. Аудитория: teamlead (для декомпозиции задач) и разработчики (для реализации без домыслов).

Базовые решения зафиксированы в [brief.md](./brief.md). Здесь — конкретика.

---

## 1. Компоненты и их ответственности

```
+-------------------+       HTTPS (TLS)         +-----------------------+
|  Android app      |  ----------------------> |   Backend             |
|  Kotlin, Compose  |  Bearer device-token     |   Spring Boot 4,      |
|                   |  JSON (REST)             |   Java 25             |
+-------------------+                          |                       |
                                               |  - AuthService        |
                                               |  - PanelService       |
                                               |  - PanelClient (WC)   |
                                               |  - CryptoService      |
                                               |  - SessionCache       |
                                               +----------+------------+
                                                          |
                                       +------------------+------------------+
                                       |                                     |
                             JDBC (localhost)                     HTTPS (TLS, cookie session)
                                       |                                     |
                                       v                                     v
                              +-----------------+                  +----------------------+
                              | PostgreSQL 17   |                  | 3x-ui panels (N шт.) |
                              | + Liquibase     |                  | /login, /panel/api/* |
                              +-----------------+                  +----------------------+
```

**Ответственности:**

- **Android app** — UI, локальное хранение device-токена (DataStore), работа только с собственным бэкендом. Никогда не ходит напрямую в панели 3x-ui.
- **Backend** — единственный клиент, который знает креды панелей. Шифрует/расшифровывает креды, логинится в панели, держит HTTP-сессии, проксирует вызовы от Android. Реализует бизнес-логику (авторизация устройств, multi-tenancy).
- **PostgreSQL** — персистентное хранилище пользователей, панелей, (опционально) audit-логов.
- **Панели 3x-ui** — внешние системы. Управляются существующим HTTP API (см. раздел 6).

**Протоколы:**

- Android ↔ Backend: HTTPS, REST/JSON, auth = Bearer device-token.
- Backend ↔ PostgreSQL: JDBC (Hikari).
- Backend ↔ 3x-ui: HTTPS, form-login → cookie-сессия (`3x-ui` cookie), JSON body для POST.

---

## 2. Структура бэкенда (Spring Boot 4, Java 25)

Single-module Maven проект. Пакет-корень: `app.threexui.panel`.

Слоистая архитектура (web → service → domain → infra). Spring WebMVC для входящего REST, Spring WebFlux `WebClient` только как HTTP-клиент для походов в 3x-ui (не тянем весь reactive-stack в контроллеры).

```
app.threexui.panel
├── PanelApplication.java
├── config/
│   ├── SecurityConfig.java         # Spring Security, DeviceTokenFilter
│   ├── WebClientConfig.java        # WebClient-фабрика, self-signed toggle
│   ├── OpenApiConfig.java          # springdoc
│   ├── Resilience4jConfig.java     # circuit breaker, retry, timeouts
│   └── CryptoConfig.java           # master-key loading
├── web/                            # REST-контроллеры и DTO
│   ├── auth/       AuthController, AuthDto
│   ├── panel/      PanelController, PanelDto
│   ├── inbound/    InboundController, InboundDto
│   ├── client/     ClientController, ClientDto
│   ├── dashboard/  DashboardController, DashboardDto
│   ├── stats/      StatsController, StatsDto
│   ├── share/      ShareController
│   └── error/      GlobalExceptionHandler, ProblemDetailFactory
├── service/                        # use-cases
│   ├── auth/       AuthService, DeviceTokenService
│   ├── panel/      PanelService, PanelSessionManager
│   ├── inbound/    InboundService
│   ├── client/     ClientService, ShareLinkBuilder
│   └── stats/      StatsService
├── domain/                         # JPA-сущности и доменные типы
│   ├── user/       User, UserRepository
│   ├── panel/      Panel, PanelRepository, EncryptedCredentials (embeddable)
│   └── audit/      AuditLog, AuditLogRepository
├── infra/                          # внешние адаптеры
│   ├── xui/                        # 3x-ui HTTP-клиент
│   │   ├── XuiClient.java          # фасад: login, listInbounds, ...
│   │   ├── XuiClientImpl.java      # WebClient-реализация
│   │   ├── XuiSession.java         # cookie + expiry
│   │   ├── XuiSessionCache.java    # Caffeine-кэш сессий по panelId
│   │   ├── XuiErrorMapper.java     # 401/500 → XuiException
│   │   └── dto/                    # DTO под API панели
│   ├── crypto/     AesGcmCipher, MasterKeyProvider
│   └── ratelimit/  RateLimitFilter  (bucket4j)
└── common/                         # утилиты, общие типы
    ├── ids/        UuidGenerator
    └── error/      ErrorCode (enum)
```

**Ключевые доменные сущности:**

- `User` — анонимный аккаунт (id, hashed_device_token, created_at, email nullable).
- `Panel` — подключение к 3x-ui (id, user_id, name, base_url, encrypted_login, encrypted_password, iv, trust_self_signed, created_at, last_login_at).
- `AuditLog` — id, user_id, panel_id, action, outcome, created_at, metadata (jsonb).

**Почему WebClient, а не RestClient/OkHttp:** WebClient нативно умеет non-blocking походы, удобен для параллельного опроса панелей в будущих фичах (пуши). Но в контроллерах — обычный blocking-стиль (`.block()` на границе), чтобы не заражать весь код реактивностью до реальной нужды.

**Зачем Caffeine** (не в стеке, новая зависимость) — для `XuiSessionCache`. Обосновано: нужен TTL + эвикшн, ручная `ConcurrentHashMap` + планировщик даст больше кода. Альтернатива — держать сессию в `Panel`-таблице, но это лишняя запись в БД на каждый re-login.

---

## 3. Структура Android-приложения (Kotlin, Compose)

**Multi-module Gradle, version catalog (`libs.versions.toml`).** Single-module на MVP оправдан размером, но feature-модули упростят включение фич из бэклога (push/FCM, email-sync) без роста app-модуля. Цена — чуть больше Gradle-конфигурации. Идём на multi-module.

```
:app                          # сборка, навигационный граф верхнего уровня, MainActivity
:core
  :core:ui                    # темы Material3, базовые компоненты (LoadingStates, ErrorBanner, QrView)
  :core:network               # Retrofit, OkHttp, Interceptor'ы (auth, logging), SSL-toggle
  :core:data                  # DataStore, репозитории общего назначения, модели API
  :core:common                # DispatcherProvider, Result-обёртки, ошибочные типы
  :core:testing               # MockWebServer, fakes
:feature
  :feature:auth               # первый запуск, получение device-токена, (заглушка) привязка email
  :feature:panels             # список/добавление/редактирование панелей
  :feature:dashboard          # статус, CPU/RAM/сеть, uptime выбранной панели
  :feature:inbounds           # CRUD инбаундов, toggle enable
  :feature:clients            # CRUD клиентов, сброс трафика, экран деталей клиента
  :feature:share              # QR + подписочная ссылка (экран / bottom sheet)
  :feature:stats              # графики трафика (по клиентам/инбаундам)
```

**Правила зависимостей:** `:feature:*` зависят только от `:core:*`. `:app` знает все `:feature:*`. `:feature:*` между собой не зависят. Если нужен переход — через интерфейс навигации в `:app`.

**Навигация:** Jetpack Navigation Compose. Корневой граф в `:app` подключает sub-graph'ы из feature-модулей через функции-расширения `NavGraphBuilder.panelsGraph()`, `NavGraphBuilder.inboundsGraph()` и т.д. Deep-links пока не нужны, оставим hook'и на будущее (sharing-ссылки).

**DI (Hilt):** `@HiltAndroidApp` в `:app`. В feature-модулях — `@Module @InstallIn(SingletonComponent::class)` для репозиториев. ViewModel'и через `@HiltViewModel`. `:core:network` предоставляет `Retrofit`, `OkHttpClient`, `AuthInterceptor`.

**Архитектура экрана:** MVI-lite (Compose + ViewModel + `StateFlow<UiState>`). Без тяжёлого MVI-фреймворка.

**Хранение токена:** `DataStore<Preferences>` в `:core:data`, ключ `device_token`. Токен plain (не secret в нашей модели, см. раздел 9).

**Коннект с сетью:** Retrofit + kotlinx.serialization converter, OkHttp `AuthInterceptor` добавляет `Authorization: Bearer <token>`. Ошибки маппятся в `DomainError` sealed class через `Call.awaitResponseOrError()` helper.

**minSdk:** 26 (Android 8.0). Обосновано: покрывает ~95% активных устройств, даёт стабильный TLS 1.2/1.3, java.time, нормальный AndroidX. Предложение зафиксировать в брифе.

---

## 4. Схема БД (PostgreSQL 17 + Liquibase)

Liquibase changelog: `db/changelog/db.changelog-master.yaml`, разбитие по релизам (`changes/001-initial.sql`, ...).

### Таблицы

**users**
- `id UUID PRIMARY KEY`
- `device_token_hash TEXT NOT NULL UNIQUE` — SHA-256 от plaintext-токена
- `email TEXT NULL UNIQUE` — под будущую привязку (бэклог)
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `last_seen_at TIMESTAMPTZ NULL`

**panels**
- `id UUID PRIMARY KEY`
- `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE`
- `name TEXT NOT NULL`
- `base_url TEXT NOT NULL` — напр. `https://panel.example.com:2053`
- `login_cipher BYTEA NOT NULL` — AES-GCM ciphertext (IV || ct || tag)
- `password_cipher BYTEA NOT NULL`
- `key_version SMALLINT NOT NULL DEFAULT 1` — для ротации мастер-ключа
- `trust_self_signed BOOLEAN NOT NULL DEFAULT false`
- `last_login_at TIMESTAMPTZ NULL`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- UNIQUE `(user_id, name)` — имя уникально в рамках пользователя

**audit_log**
- `id BIGSERIAL PRIMARY KEY`
- `user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL`
- `panel_id UUID NULL REFERENCES panels(id) ON DELETE SET NULL`
- `action TEXT NOT NULL` — `LOGIN`, `PANEL_ADD`, `INBOUND_CREATE`, ...
- `outcome TEXT NOT NULL` — `OK`, `FAIL`
- `metadata JSONB NULL`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- индекс на `(user_id, created_at DESC)`

### DDL ключевых таблиц

```sql
CREATE TABLE users (
    id                 UUID        PRIMARY KEY,
    device_token_hash  TEXT        NOT NULL UNIQUE,
    email              TEXT        NULL UNIQUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at       TIMESTAMPTZ NULL
);

CREATE TABLE panels (
    id                  UUID         PRIMARY KEY,
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                TEXT         NOT NULL,
    base_url            TEXT         NOT NULL,
    login_cipher        BYTEA        NOT NULL,
    password_cipher     BYTEA        NOT NULL,
    key_version         SMALLINT     NOT NULL DEFAULT 1,
    trust_self_signed   BOOLEAN      NOT NULL DEFAULT false,
    last_login_at       TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT panels_user_name_uniq UNIQUE (user_id, name)
);
CREATE INDEX panels_user_id_idx ON panels(user_id);

CREATE TABLE audit_log (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     UUID        NULL REFERENCES users(id) ON DELETE SET NULL,
    panel_id    UUID        NULL REFERENCES panels(id) ON DELETE SET NULL,
    action      TEXT        NOT NULL,
    outcome     TEXT        NOT NULL,
    metadata    JSONB       NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_log_user_created_idx ON audit_log(user_id, created_at DESC);
```

Сессии 3x-ui **не храним в БД** — только in-memory в `XuiSessionCache` (Caffeine). Теряются при рестарте — пере-логинимся лениво при первом запросе.

---

## 5. Шифрование кредов панелей

**Алгоритм:** AES-256-GCM. Authenticated encryption — защищает и конфиденциальность, и целостность; GCM включает тег.

**Формат хранения в `BYTEA`:**

```
[ 12 bytes IV ] [ N bytes ciphertext ] [ 16 bytes GCM tag ]
```

IV — `SecureRandom`, 96 бит (рекомендация NIST для GCM). Ciphertext+tag — нативный выход `Cipher.doFinal()` в режиме GCM (тег присоединён в конце).

`key_version` хранится в отдельной колонке — не вшиваем в BLOB, чтобы SQL-селекты могли фильтровать записи со старым ключом при миграции.

**Мастер-ключ:**

- Источник — env-переменная `PANEL_MASTER_KEY_V1` (base64 от 32 байт). Чтение на старте через `MasterKeyProvider`.
- Для прода — монтировать из Docker secret / systemd EnvironmentFile с `0400`. Секрет-менеджеры (Vault/KMS) — откладываем (см. трейд-оффы).
- Ключ никогда не логируется. Toggle fail-fast: если ключ не задан или короче 32 байт — приложение не стартует.

**Ротация:**

1. Добавляем env `PANEL_MASTER_KEY_V2`, деплоим. Приложение читает оба ключа.
2. Фоновый джоб (или одноразовая admin-команда) выбирает записи с `key_version = 1`, расшифровывает ключом v1, зашифровывает ключом v2, обновляет `key_version = 2`.
3. После миграции всех строк — удаляем v1 из env.

В MVP сам джоб ротации не реализуем — только закладываем `key_version` и `MasterKeyProvider`, способный отдать ключ по версии.

**Зависимость:** штатный JCE (в JDK 25 — Bouncy Castle не нужен).

---

## 6. Работа с 3x-ui API

**Аутентификация в панели.** 3x-ui — Go-приложение на gin, `/login` принимает `application/x-www-form-urlencoded` форму с полями `username` и `password`, выставляет cookie-сессию `3x-ui` (jwt/session внутри). Все последующие запросы идут с этой cookie. Подробности: [Configuration wiki](https://github.com/MHSanaei/3x-ui/wiki/Configuration), [api.go](https://github.com/MHSanaei/3x-ui/blob/main/web/controller/api.go), [Postman-коллекция](https://documenter.getpostman.com/view/16802678/2s9YkgD5jm).

**Переиспользование сессии:**

- `XuiSessionCache` (Caffeine, TTL 50 минут, панели 3x-ui по умолчанию живут час) хранит `panelId -> XuiSession(cookie, issuedAt, expiresAt)`.
- `XuiClient` перед запросом берёт сессию из кэша. Если нет — логинится, кладёт в кэш.
- При ответе `401` или HTML-редиректе на `/login` — инвалидируем кэш, логинимся, повторяем запрос один раз. Если снова 401 — считаем креды невалидными, возвращаем `XuiAuthenticationException`.

**Таймауты:**

- connect: 5s, read: 15s, overall request: 20s.
- Конфигурируются через properties `panel.http.connect-timeout`, `panel.http.read-timeout`.

**Retry-политика (Resilience4j):**

- Retry: 2 попытки на idempotent-запросы (GET) при `IOException`/`5xx`. Бэкофф 200ms * 2^n.
- Для POST (мутации) ретраев нет по умолчанию — только на `401` (re-login), и только единожды.

**Circuit breaker (Resilience4j):**

- Per-panel circuit breaker (ключ — `panelId`). Окно 20 запросов, порог 50% ошибок, open — 30s, half-open — 3 пробных запроса.
- При open — сразу `PANEL_UNAVAILABLE` без хождения в сеть.

**TLS:**

- По умолчанию — строгая проверка цепочки.
- Если у `panel.trust_self_signed = true` — `WebClient` для этой панели собирается с `InsecureTrustManagerFactory`. Это отдельный per-panel `WebClient` (или `HttpClient`), кэшируется в `PanelWebClientFactory`.

### Используемые эндпоинты 3x-ui

Базовый путь админских API: `/panel/api/inbounds` (защищён middleware `checkAPIAuth`).

| Операция | Метод/путь | Примечание |
|---|---|---|
| Логин | `POST /login` (form: username, password) | Возвращает Set-Cookie `3x-ui` |
| Список инбаундов | `GET /panel/api/inbounds/list` | Содержит и инбаунды, и их клиентов |
| Инбаунд по id | `GET /panel/api/inbounds/get/:id` | |
| Создать инбаунд | `POST /panel/api/inbounds/add` | JSON |
| Обновить инбаунд | `POST /panel/api/inbounds/update/:id` | |
| Удалить инбаунд | `POST /panel/api/inbounds/del/:id` | |
| Добавить клиента | `POST /panel/api/inbounds/addClient` | `{id: inboundId, settings: "<json-string>"}` |
| Обновить клиента | `POST /panel/api/inbounds/updateClient/:clientId` | `clientId` = UUID клиента |
| Удалить клиента | `POST /panel/api/inbounds/:id/delClient/:clientId` | |
| Сбросить трафик клиента | `POST /panel/api/inbounds/:id/resetClientTraffic/:email` | |
| Сбросить весь трафик | `POST /panel/api/inbounds/resetAllTraffics` | |
| Трафик клиента по email | `GET /panel/api/inbounds/getClientTraffics/:email` | |
| Трафик клиента по id | `GET /panel/api/inbounds/getClientTrafficsById/:id` | |
| IP-адреса клиента | `POST /panel/api/inbounds/clientIps/:email` | |
| Очистить IP клиента | `POST /panel/api/inbounds/clearClientIps/:email` | |
| Статус сервера | `POST /server/status` | CPU, RAM, uptime, Xray state |
| Онлайн-клиенты | `POST /panel/inbound/onlines` | Для дашборда |

Источники путей: [api.go](https://github.com/MHSanaei/3x-ui/blob/main/web/controller/api.go), [DeepWiki: Inbound Management](https://deepwiki.com/MHSanaei/3x-ui/4.1-inbound-management), [Postman: Inbounds](https://www.postman.com/hsanaei/3x-ui/request/vmckurc/inbounds), [3xui-api-client (ref implementation)](https://github.com/iamhelitha/3xui-api-client).

**Подписочная ссылка** строится на бэке по данным клиента и инбаунда в формате VLESS/VMess URI (`vless://uuid@host:port?...#remark`) — спецификация Xray. QR делается в приложении из этой строки (библиотека `zxing-android-embedded` — новая зависимость, обосновано: стандарт для QR на Android).

---

## 7. REST API между мобильным приложением и бэкендом

Базовый путь: `/api/v1`. Формат: JSON. Auth: `Authorization: Bearer <device-token>` кроме `/auth/bootstrap`. Ошибки — `application/problem+json` (см. раздел 8).

OpenAPI-спека будет генерироваться `springdoc-openapi`, доступна на `/v3/api-docs` (в dev-профиле + Swagger UI).

### 7.1 Auth

**POST `/api/v1/auth/bootstrap`** — выдача анонимного токена при первом запуске.

Request:
```json
{ "deviceInfo": { "platform": "android", "osVersion": "14", "appVersion": "1.0.0" } }
```

Response `200`:
```json
{ "token": "b9f2...c0a1", "userId": "3b2a9f2c-8a4e-4d22-9f1a-2a2b6f6d7d10" }
```

**POST `/api/v1/auth/email/link`** *(заглушка, 501 на MVP — возвращает problem+json `not-implemented`)* — для будущей привязки email.

Request:
```json
{ "email": "user@example.com" }
```

### 7.2 Panels

**GET `/api/v1/panels`** — список панелей текущего пользователя.
```json
[
  { "id": "...", "name": "Main VPS", "baseUrl": "https://panel.example.com:2053", "trustSelfSigned": false, "lastLoginAt": "2026-04-20T15:00:00Z" }
]
```

**POST `/api/v1/panels`** — добавление. Бэк сразу пробует залогиниться; при неудаче — 422 с problem+json.
```json
{ "name": "Main VPS", "baseUrl": "https://panel.example.com:2053", "login": "admin", "password": "s3cret", "trustSelfSigned": false }
```
Response `201`:
```json
{ "id": "...", "name": "Main VPS", "baseUrl": "...", "trustSelfSigned": false }
```

**PATCH `/api/v1/panels/{panelId}`** — редактирование (любое из полей, включая смену пароля).

**DELETE `/api/v1/panels/{panelId}`** — удаление. Каскадно чистит сессию в кэше.

### 7.3 Dashboard

**GET `/api/v1/panels/{panelId}/dashboard`** — статус Xray + ресурсы.
```json
{
  "xray": { "state": "running", "version": "1.8.11", "errorMsg": null },
  "system": { "cpuPercent": 12.4, "memUsed": 812000000, "memTotal": 2048000000, "uptimeSeconds": 864210 },
  "network": { "bytesSent": 12345678, "bytesRecv": 23456789 },
  "onlineClients": 7
}
```

### 7.4 Inbounds

**GET `/api/v1/panels/{panelId}/inbounds`** — список (с вложенными клиентами).

**POST `/api/v1/panels/{panelId}/inbounds`** — создать.
```json
{ "remark": "VLESS-TLS", "port": 443, "protocol": "vless", "enable": true, "settings": { /* protocol-specific */ }, "streamSettings": { /* ... */ }, "sniffing": { /* ... */ } }
```

**PATCH `/api/v1/panels/{panelId}/inbounds/{inboundId}`** — обновить (включая toggle `enable`).

**DELETE `/api/v1/panels/{panelId}/inbounds/{inboundId}`** — удалить.

### 7.5 Clients

**POST `/api/v1/panels/{panelId}/inbounds/{inboundId}/clients`** — добавить клиента.
```json
{ "email": "alice@example.com", "totalGB": 100, "expiryTime": 1735689600000, "limitIp": 2, "flow": "xtls-rprx-vision" }
```

**PATCH `/api/v1/panels/{panelId}/inbounds/{inboundId}/clients/{clientId}`**

**DELETE `/api/v1/panels/{panelId}/inbounds/{inboundId}/clients/{clientId}`**

**POST `/api/v1/panels/{panelId}/inbounds/{inboundId}/clients/{clientId}/reset-traffic`**

### 7.6 Share

**GET `/api/v1/panels/{panelId}/inbounds/{inboundId}/clients/{clientId}/share`** — подписочная строка.
```json
{ "uri": "vless://uuid@host:443?...#remark", "remark": "Main VPS / alice" }
```
QR рендерится на клиенте из `uri` — меньше трафика, не нужен image endpoint.

### 7.7 Stats

**GET `/api/v1/panels/{panelId}/stats/clients?period=day|week|month`** — суммарный трафик по клиентам.
```json
[ { "clientId": "...", "email": "alice@example.com", "up": 1234567, "down": 7654321 } ]
```

**GET `/api/v1/panels/{panelId}/stats/inbounds?period=...`** — по инбаундам.

### Общие коды ошибок

- `400` — валидация ввода
- `401` — нет/невалидный device-token
- `403` — panel принадлежит другому пользователю
- `404` — panel/inbound/client не найден
- `422` — невалидные креды панели / неприменимая операция
- `502` — ошибка связи с панелью
- `503` — circuit breaker open
- `504` — таймаут панели
- `429` — rate limit

---

## 8. Обработка ошибок

**Формат — RFC 7807 `application/problem+json`** с расширениями.

Пример:
```json
{
  "type": "https://docs.3xui-panel/errors/panel-unreachable",
  "title": "Panel is unreachable",
  "status": 502,
  "detail": "Connection refused after 3 attempts",
  "instance": "/api/v1/panels/abc/inbounds",
  "code": "PANEL_UNREACHABLE",
  "panelId": "abc",
  "traceId": "0a1b2c3d"
}
```

**Централизация:** `@RestControllerAdvice GlobalExceptionHandler` + фабрика `ProblemDetailFactory`. Внутреннее — `ErrorCode` enum (`INVALID_TOKEN`, `PANEL_UNREACHABLE`, `PANEL_AUTH_FAILED`, `PANEL_TIMEOUT`, `CIRCUIT_OPEN`, `VALIDATION_FAILED`, `RATE_LIMITED`, ...).

**Маппинг:**

| 3x-ui / инфра | Бэкенд (HTTP) | `code` | UI сообщение |
|---|---|---|---|
| `401` от панели / redirect на /login после re-login | `422` | `PANEL_AUTH_FAILED` | "Неверные логин или пароль панели" |
| `ConnectException`, UnknownHost | `502` | `PANEL_UNREACHABLE` | "Не удаётся подключиться к панели" |
| `TimeoutException` | `504` | `PANEL_TIMEOUT` | "Панель не отвечает" |
| Resilience4j circuit open | `503` | `PANEL_CIRCUIT_OPEN` | "Панель временно недоступна, попробуйте позже" |
| Валидация body | `400` | `VALIDATION_FAILED` | "Проверьте поля: <имена>" |
| Неизвестная 5xx | `502` | `PANEL_BAD_RESPONSE` | "Панель вернула ошибку" |

**Android:** `DomainError` sealed class, `ErrorMapper` читает `code` из problem+json и маппит в string-ресурс. Snackbar для транзиентных, диалог для блокирующих (auth failed).

---

## 9. Безопасность

**TLS.**

- Походы в панель — всегда HTTPS. HTTP-URL — валидатор отклонит на создании.
- По умолчанию — строгая проверка. Чекбокс "доверять self-signed сертификату" в форме добавления панели, помеченный предупреждением, сохраняется в `panels.trust_self_signed`. Отражается в UI иконкой на карточке панели.

**Device token.**

- Генерация: `SecureRandom`, 32 байта, base64url. Выдаётся один раз при `POST /auth/bootstrap`.
- Хранится на клиенте в `DataStore` (plain). Android DataStore не зашифрован по умолчанию; шифровать через `EncryptedDataStore`/KeyStore — оверкилл для anonymous токена: он не secret (сам по себе = идентификатор учётки), его потеря эквивалентна потере устройства, а не утечке «внутренних данных».
- В БД — `sha256(token)` в `users.device_token_hash`, UNIQUE-индекс. Лукап при каждом запросе через `DeviceTokenFilter` (в Spring Security Filter Chain). Кэш аутентификации — Caffeine, 1 мин (инвалидация при DELETE пользователя — для MVP не обязательна, нет эндпоинта).
- Почему хэш, а не plain: если БД утечёт, токены нельзя использовать напрямую.
- Почему не JWT: нечего подписывать (нет ролей/expiry), добавит сложности без выгоды.

**Rate limiting (bucket4j + Caffeine).**

- `/auth/bootstrap`: 10 запросов на IP в минуту.
- `/auth/email/link` (заглушка): 5 запросов на user в час — защита от brute force будущей привязки.
- Остальные `/api/v1/*` per-user: 120 RPM. На MVP достаточно.

**Защита от brute force кредов панелей.**

- На создание/обновление панели — один реальный поход login. Если за 5 минут по одному `user_id` >10 неуспешных login → 429 на добавление/обновление.

**Хранение конфиденциального.**

- Креды панелей — AES-GCM в БД (раздел 5).
- Логи не содержат login/password/cookie панели — `WebClient` ExchangeFilterFunction маскирует заголовки `Cookie`, `Set-Cookie`, тело `/login`.

**CORS.** Бэк отвечает только мобильному клиенту — CORS default-disabled. Swagger UI — только в dev-профиле.

---

## 10. Деплой и инфра (MVP)

**Цель MVP:** один VPS (2 vCPU, 2GB RAM) с docker + docker-compose. Managed БД не нужна.

`/deploy/docker-compose.yml` (dev + prod одинаковые по форме, различаются `.env`):

- `postgres:17-alpine`, том `pgdata`, healthcheck.
- `backend` — образ из `./backend/Dockerfile`, `depends_on: postgres (service_healthy)`.
- (опц.) `caddy`/`nginx` как TLS-termination. Можно на MVP пустить сразу backend за Caddy с автоматическим Let's Encrypt.

**Dockerfile бэкенда:** multi-stage.
1. `eclipse-temurin:25-jdk` → Maven build (`mvn -B -DskipTests package`).
2. `eclipse-temurin:25-jre` → копируем fat-jar, `ENTRYPOINT java -XX:+UseZGC -jar app.jar`.

**Переменные окружения (обязательные):**

- `PANEL_MASTER_KEY_V1` — base64, 32 байта
- `PANEL_DB_URL` — `jdbc:postgresql://postgres:5432/panel`
- `PANEL_DB_USER`, `PANEL_DB_PASSWORD`
- `PANEL_SERVER_PORT` (default 8080)
- `PANEL_HTTP_CONNECT_TIMEOUT`, `PANEL_HTTP_READ_TIMEOUT` — для 3x-ui клиента
- `PANEL_RATE_LIMIT_BOOTSTRAP_PER_MIN` и т.п.
- `SPRING_PROFILES_ACTIVE=prod`

Dev-профиль: `application-dev.yml` включает Swagger UI, `DEBUG` на `app.threexui`.

---

## 11. CI/CD (GitHub Actions)

Три workflow в `/.github/workflows/`:

### `backend-build.yml`

- Trigger: `push` на `main`, `pull_request` в `main`, пути `backend/**`.
- Jobs:
  - `build-test` — `actions/setup-java@v4` (temurin 25), кэш Maven, `mvn -B verify`. Публикация jacoco отчёта как артефакт.
  - `lint` — `mvn spotless:check` (Spotless + Google Java Format).
- Docker image сборка — отдельным job'ом на теге `v*` с push в GHCR (не обязательно на самый MVP, но заложить).

### `android-release.yml`

- Trigger: `push` на `main` (сборка debug-APK как артефакт), tag `v*` (release + публикация в GitHub Releases).
- Steps:
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` (temurin 21 — Android Gradle Plugin на 21)
  3. `gradle/actions/setup-gradle@v4`
  4. Декод keystore:
     ```
     echo "$SIGNING_KEYSTORE_BASE64" | base64 -d > app/signing.keystore
     ```
  5. `./gradlew :app:assembleRelease` с env:
     - `SIGNING_KEYSTORE_FILE=app/signing.keystore`
     - `SIGNING_STORE_PASSWORD=${{ secrets.SIGNING_STORE_PASSWORD }}`
     - `SIGNING_KEY_PASSWORD=${{ secrets.SIGNING_KEY_PASSWORD }}`
     - `SIGNING_KEY_ALIAS=${{ secrets.SIGNING_KEY_ALIAS }}`
  6. На теге `v*` — `softprops/action-gh-release@v2`, аплоад `app-release.apk`.
  7. На `main` (не тег) — `actions/upload-artifact@v4`.
- Имена секретов GitHub (уже заведены): `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS`.
- Конфигурация подписи в `app/build.gradle.kts` читает именно эти env-vars через `signingConfigs.release`.

### `lint.yml` (общий)

- Android: `./gradlew ktlintCheck detekt`.
- Backend: `mvn spotless:check`.
- Markdown: `markdownlint` (опционально).

Все три workflow'а — required checks для merge в `main`.

---

## 12. Трейд-оффы и альтернативы

**Сознательные упрощения:**

- **Single-region БД без реплик.** MVP-пользователи — единицы/десятки. Репликация — позже.
- **Сессии 3x-ui только в памяти.** При рестарте бэка — массовый re-login. Приемлемо: логин быстрый, нагрузка низкая. Альтернатива (Redis) — лишний сервис.
- **Мастер-ключ из env, без KMS/Vault.** Самое простое. Осознанный риск: компрометация хоста = компрометация всех кредов. Ротация заложена, миграция на Vault — прозрачная замена `MasterKeyProvider`.
- **Device-token без expiry/refresh.** Упрощает первый запуск. Риск: утерянное устройство = вечный доступ. Митигация в бэклоге — привязка email + явный revoke.
- **RFC 7807 вместо собственного формата ошибок.** Стандарт, Spring Boot умеет из коробки (`ProblemDetail`).
- **Spring WebMVC для входа, WebFlux только как HTTP-клиент.** Избегаем реактивщины в контроллерах, где она не нужна.
- **Multi-module Android сразу.** Чуть дороже на старте, дешевле при росте. Обосновано фичами из бэклога (FCM, email-sync — будут новые модули).

**Отвергнуто:**

- **Kotlin на бэке** — бриф явно фиксирует Java 25, не меняем.
- **gRPC между мобилкой и бэком** — REST/JSON проще для отладки, мобильному клиенту не нужна производительность gRPC.
- **JWT для device-token** — нет полезной нагрузки, простые opaque-токены + hash в БД проще и безопаснее (моментальный revoke через DELETE).
- **Хранить сессии 3x-ui в БД** — лишние запись/селект на каждый запрос; TTL логика тоже нужна — Caffeine её даёт даром.
- **Прямой доступ Android → 3x-ui** — нарушает требование брифа (бэкенд шифрует/держит креды) и усложняет мультитенантность.
- **Single-module Android** — на двух фичах ок, на шести (MVP) + бэклог уже тесно.

**Риски и митигации:**

- *API 3x-ui недокументирован/меняется по мажоркам.* → Adapter-слой `XuiClient` + DTO-проекции. Интеграционные тесты против локально поднятого 3x-ui в docker.
- *Self-signed сертификаты у пользователей.* → Явный toggle per-panel, предупреждение в UI.
- *Утечка мастер-ключа.* → Env с правами `0400`, не логируется, ротация заложена.
- *Масштаб сессий (100+ панелей).* → Caffeine `maximumSize=10_000`, LRU. Уже достаточно для MVP и в разы больше.
- *Долгие вызовы 3x-ui блокируют тред-пул.* → Таймауты + circuit breaker + отдельный bounded executor для блокирующих участков.

---

## Для тимлида: разбиение верхнего уровня

- [ ] **Backend: скелет проекта** — Maven, Spring Boot 4, Java 25, модульная структура, базовый `application.yml`, Liquibase, Dockerfile, docker-compose.
- [ ] **Backend: auth** — `users`, `DeviceTokenFilter`, `POST /auth/bootstrap`, rate-limit, `/auth/email/link` 501-заглушка.
- [ ] **Backend: crypto** — `AesGcmCipher`, `MasterKeyProvider`, тесты на round-trip и fail-fast при пустом ключе.
- [ ] **Backend: panels CRUD** — DB, REST, валидация URL, проба логина при create/update.
- [ ] **Backend: XuiClient** — WebClient + cookie-сессия, `XuiSessionCache`, Resilience4j (retry/circuit), TLS toggle.
- [ ] **Backend: inbounds + clients + share** — REST → XuiClient маппинги, построение VLESS/VMess URI.
- [ ] **Backend: dashboard + stats** — агрегация из `/server/status`, `/onlines`, трафик по инбаундам/клиентам.
- [ ] **Backend: ошибки и observability** — `GlobalExceptionHandler`, problem+json, structured logging, traceId.
- [ ] **Android: скелет multi-module** — Gradle catalog, `:app`, `:core:*`, `:feature:*`, Hilt, Compose, Material3, Navigation.
- [ ] **Android: auth & network core** — `:core:network` (Retrofit, AuthInterceptor, SSL config), `:feature:auth` (bootstrap, DataStore).
- [ ] **Android: panels** — список/создание/редактирование/удаление.
- [ ] **Android: dashboard**
- [ ] **Android: inbounds CRUD + enable toggle**
- [ ] **Android: clients CRUD + reset traffic**
- [ ] **Android: share (QR + copy link)**
- [ ] **Android: stats (графики)** — выбор библиотеки графиков (Vico или MPAndroidChart fork для Compose).
- [ ] **CI/CD** — `backend-build.yml`, `android-release.yml`, `lint.yml`; протестировать подпись APK с существующими секретами.
- [ ] **Инфра** — docker-compose prod, Caddy c автоматическим TLS, инструкция деплоя в README.
- [ ] **QA** — интеграционные тесты бэка против контейнера 3x-ui; smoke-тест Android на эмуляторе + реальном устройстве.

---

## Источники

- [3x-ui репозиторий](https://github.com/MHSanaei/3x-ui)
- [3x-ui: web/controller/api.go](https://github.com/MHSanaei/3x-ui/blob/main/web/controller/api.go)
- [3x-ui Wiki: Configuration](https://github.com/MHSanaei/3x-ui/wiki/Configuration)
- [3x-ui Postman docs](https://documenter.getpostman.com/view/16802678/2s9YkgD5jm)
- [DeepWiki: Inbound Management](https://deepwiki.com/MHSanaei/3x-ui/4.1-inbound-management)
- [3xui-api-client (reference impl)](https://github.com/iamhelitha/3xui-api-client)
- [Spring Boot 4 docs](https://docs.spring.io/spring-boot/)
- [Resilience4j](https://resilience4j.readme.io/)
- [RFC 7807 — Problem Details for HTTP APIs](https://datatracker.ietf.org/doc/html/rfc7807)
