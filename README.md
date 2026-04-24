# 3xui Panel

Мобильный Android-клиент для удалённого управления серверами [3x-ui](https://github.com/MHSanaei/3x-ui) через собственный бэкенд.

## Статус

MVP в разработке. Фаза: сбор требований и проектирование.

## Архитектура (коротко)

```
Android app  ──HTTPS──▶  Backend (Spring Boot)  ──HTTPS──▶  3x-ui panels
```

- **Android app** — Kotlin, Jetpack Compose (Material 3), Coroutines/Flow, Hilt, Retrofit, DataStore.
- **Backend** — Java 25, Spring Boot 4, PostgreSQL 17, Liquibase, WebClient.
- **Аутентификация в приложении** — анонимный токен, выдаваемый бэкендом при первом запуске; опционально привязка email для синка между устройствами (в бэклоге).
- **Подключение панелей** — пользователь добавляет панели 3x-ui (URL + логин + пароль); креды хранятся на бэкенде в зашифрованном виде, бэкенд проксирует запросы к панелям.

## Документы

- [docs/brief.md](docs/brief.md) — бриф и состав MVP.

## Сборка APK

Будет настроена позже: GitHub Actions собирает подписанный release-APK на push в `main` и на git-тег `v*`, публикует в GitHub Releases.
