# Бриф: 3xui Panel

Мобильное Android-приложение для удалённого управления серверами 3x-ui (аналог функционала веб-панели). Без собственного бэкенда — приложение ходит напрямую в API панелей.

## Сценарий использования

- Пользователь устанавливает Android-приложение.
- Добавляет одну или несколько панелей 3x-ui (URL + логин + пароль).
- Креды и данные о панелях хранятся локально на устройстве в зашифрованной БД.
- Приложение логинится в каждую панель по HTTP (cookie-based сессия), поддерживает сессию и проксирует вызовы к её API.
- Пользователь управляет инбаундами, клиентами, смотрит статистику и статус — через мобильный UI.

## Границы

- **Только админская функциональность панели.** Приложение не является VPN-клиентом и не подключается к Xray-core как пользователь.
- **Без разграничения ролей.** Если пользователь добавил панель — у него полный доступ к её функционалу (как если бы он залогинился в веб-UI панели).
- **Без бэкенда.** Один экземпляр приложения = один локальный «пользователь». Нет мультитенантности на уровне сервиса, нет чужих данных.
- **Без синка между устройствами.** Сменил телефон или переустановил — данные теряются, если не сделал экспорт. Ручной экспорт/импорт — в бэклоге.
- **Push-уведомления — best-effort.** Реализуются через локальный `WorkManager`-поллинг. Истинные push невозможны без серверной части.

## Архитектура

```
Android app  ──HTTPS──▶  3x-ui panels
```

- Приложение общается с панелями напрямую по HTTPS. Для каждой панели поддерживается своя HTTP-сессия (cookie после логина).
- Все данные (список панелей, креды, кэш) хранятся локально в Room-БД, зашифрованной через SQLCipher. Passphrase БД защищена Android Keystore (AES-256-GCM, non-exportable ключ).
- Сетевой слой — OkHttp + Retrofit + kotlinx.serialization, отдельный `OkHttpClient` на панель (свой `CookieJar`, свой TrustManager под self-signed).

## Стек

### Mobile
- Kotlin 2.x
- Jetpack Compose, Material 3
- Coroutines / Flow
- Hilt (DI)
- Jetpack Navigation Compose
- Retrofit + OkHttp + kotlinx.serialization
- Room + SQLCipher (шифрованная БД)
- DataStore (мелкие несекретные настройки)
- WorkManager (поллинг для уведомлений — бэклог)
- Vico (графики статистики)
- ZXing (QR-коды)
- Android Keystore (для защиты passphrase БД)
- min SDK: 26 (Android 8.0)

### CI/CD
- GitHub Actions.
- Workflow на push в `main` и на git-тег `v*` — собирает release-APK, подписывает (keystore в секретах `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS`), публикует в GitHub Releases — чтобы скачать APK на телефон ссылкой.

## MVP-фичи (доставлены в v1.0)

1. ✅ **Мультипанель** — несколько панелей, переключение, активная панель.
2. ✅ **Дашборд** — статус Xray, CPU/RAM/сеть, аптайм + история метрик (CPU, MEM, Net In/Out, Online) с сеткой значений.
3. ✅ **Инбаунды** — CRUD, toggle enable, 10 протоколов (VMess, VLESS, Trojan, Shadowsocks, WireGuard, Hysteria v2, Mixed, HTTP, Tunnel, TUN), copy clients из другого инбаунда, импорт JSON.
4. ✅ **Клиенты** — CRUD по всем протоколам, traffic stats, сброс трафика, истечение по сроку/объёму, lastOnline.
5. ✅ **Шаринг** — QR + подписочная ссылка (server-side `getClientLinks` + локальный fallback).
6. ✅ **Статистика трафика** — графики по клиентам/инбаундам, тап-показ значения столбика.

## Доставлено сверх MVP

- **Multi-node management** — список нод, добавление, тест, history-чарт каждой ноды.
- **Panel settings** — subscription/Telegram/web/2FA, смена кредов, рестарт панели.
- **API tokens** — CRUD, авто-привязка при создании панели.
- **Custom geo sources** — управление + скачивание.
- **System actions** — сброс всего трафика, обновление панели, install Xray, бэкап в TG-бот.
- **Auto-backup** `x-ui.db` всех панелей (Daily/Weekly, SAF).
- **2FA OTP** при add/edit/rePin панели.
- **WebSocket realtime** — статус-push, notifications-toast, инвалидация кэша inbounds/clients.
- **App lock** — PIN/биометрия при запуске.
- **In-app update** — проверка GitHub Releases + скачивание APK + системный установщик.

## Бэклог

- **2FA OTP в interceptor auto-relogin** — сейчас MVP покрыт только в panel-add/edit/rePin; если сессия 2FA-панели истечёт mid-flight, пользователь увидит 401. Нужен глобальный modal-overlay + retry login с OTP.
- **PQ/ECH/VLESS-Enc генераторы** в Add Inbound — нужны когда UI-потребитель появится.
- **Backend и синк** — не планируется. Один экземпляр приложения = один локальный «пользователь».
- **Push-уведомления** — только локальные через WorkManager (лимит трафика, истечение). Истинные push требуют сервера.
