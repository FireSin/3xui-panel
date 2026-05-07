# MVP-6b: Traffic graphs (исторические графики)

Расширение `stats.md`. MVP-6 показывал текущие cumulative-счётчики; MVP-6b добавляет дельта-сэмплер раз в час, дневные агрегаты в зашифрованной БД и графики на их основе.

## 1. Goals / Non-goals

**Goals.** Сэмплировать `up`/`down` с каждой панели через существующий `XuiClient.fetchInbounds`, считать дельты per-inbound и per-client, складывать в дневные баккеты. На `:feature:stats` рисовать столбчатые/линейные графики 7/30/90 дней. Drill-down: тап по `ClientRow` → отдельный экран с графиком клиента. Хранить ≤ 90 дней с автопуржем.

**Non-goals.** Server-wide aggregate (сумма по панели), уведомления о превышении, экспорт CSV/JSON — backlog. Backfill истории за период до установки невозможен (3x-ui не отдаёт snapshots).

## 2. Data model

Две сущности в Room v3, потому что у них разная семантика: «последнее cumulative» — одна строка на скоуп с частыми upsert; дневной баккет — append/inc, читается батчами при отрисовке.

### `traffic_state` — last-known cumulative per scope

| Поле | Тип |
|---|---|
| `panel_id` | TEXT NOT NULL, FK → `panels.id` ON DELETE CASCADE |
| `scope_kind` | TEXT NOT NULL — `INBOUND` / `CLIENT` |
| `scope_key` | TEXT NOT NULL — inbound id (как `String`) или email (для CLIENT) |
| `inbound_id` | INTEGER — id родительского inbound (для CLIENT); null для INBOUND |
| `last_up`, `last_down` | INTEGER NOT NULL — cumulative в момент последнего сэмпла |
| `last_sampled_at` | INTEGER NOT NULL — epoch millis |

PK: `(panel_id, scope_kind, scope_key)`. Индекс: `(panel_id, scope_kind, inbound_id)` для drill-down.

### `traffic_daily` — append/upsert дневной агрегат

| Поле | Тип |
|---|---|
| `panel_id` | TEXT NOT NULL, FK → `panels.id` ON DELETE CASCADE |
| `scope_kind`, `scope_key`, `inbound_id` | как выше |
| `day_epoch` | INTEGER NOT NULL — UTC midnight epoch millis (см. §9) |
| `up_delta`, `down_delta` | INTEGER NOT NULL |

PK: `(panel_id, scope_kind, scope_key, day_epoch)`. Индекс `(panel_id, day_epoch)` для retention-purge.

FK на panel — есть (каскад при удалении). FK на inbound/client — нет: локальных таблиц для них не существует, 3x-ui — источник истины. Осиротевшие записи уйдут через retention.

`AppDatabase.version = 3`, миграция additive — `MIGRATION_2_3` создаёт обе таблицы с индексами. Дамп в `core/data/schemas/.../3.json` сгенерирует Room.

## 3. Sampler architecture

### Размещение

Worker и его инфра — в новом модуле **`:core:sampler`**, не в `:core:data`. Причина: WorkManager + `HiltWorkerFactory` — отдельный bounded context; тащить их в `:core:data` (где SQLCipher/Room) загрязнит граф зависимостей. `:core:sampler` зависит от `:core:data` (репо), `:core:xui` (фасад), `:core:common`. WorkManager runtime — здесь же.

### Worker

```kotlin
@HiltWorker
class TrafficSamplerWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters,
    private val panelRepository: PanelRepository,
    private val xuiClient: XuiClient,
    private val historyRepo: TrafficHistoryRepository,
    private val clock: Clock,
) : CoroutineWorker(ctx, params)
```

Регистрируется в `XuiPanelApp.onCreate()` через `TrafficSamplerScheduler.scheduleIfNeeded(workManager)`:

- `PeriodicWorkRequest` 60 мин (минимум WorkManager).
- `setInitialDelay(60s)` — не дёргать при холодном старте.
- `setBackoffCriteria(EXPONENTIAL, 30s)`.
- `Constraints: NetworkType.CONNECTED`. **Не** `UNMETERED` (нужны данные в роуминге), **не** `requiresBatteryNotLow` (нагрузка пренебрежимая, пропуски ухудшают график).
- `ExistingPeriodicWorkPolicy.KEEP`, имя `traffic-sampler`.

### Идемпотентность

В начале `doWork()` читаем `max(last_sampled_at)`. Если < 5 минут назад — `Result.success()` без работы. Защита от двойных тиков (manual debug enqueue, ребут, change-window).

### Обход панелей — последовательно

Параллельно — нельзя: `XuiSessionCache` в `:core:xui` не реентрабелен внутри одной панели; параллельный второй вызов на 401 вызовет лишний логин. У пользователя в реальном UX 1–3 панели — latency не критичен, корректность сессии — критична.

Per-panel: `xuiClient.fetchInbounds` → на `InvalidCredentials`/`Tls`/`Network`/`PinMismatch` пишем `audit_log` (`TRAFFIC_SAMPLE`/`FAIL`) и переходим к следующей панели. Глобальный SPKI kill-switch уже в `XuiClient`. На успехе — `historyRepo.commitSample(panelId, inbounds, sampledAt)` (атомарно, см. §4).

**Retry policy.** Worker возвращает `Result.retry()` **только если 0 из N панелей ответили успешно** (предположительно глобальный сбой связи) — WorkManager применит exponential backoff с 30 сек. Иначе `Result.success()` — частичный успех зачитываем, потерянные за час дельты упавших панелей принимаем. Inline per-panel retry внутри тика не делаем (ловит network-flap, но усложняет код и ест батарею; для MVP не оправдан).

## 4. Diff / aggregation logic

```
sampledAt = clock.now()
day = utcMidnight(sampledAt)
prev = stateDao.getAllForPanel(panelId).associateBy { (kind, key) }

for inbound in inbounds:
    iKey = inbound.id.toString()
    p = prev[(INBOUND, iKey)]
    (dUp, dDown) =
        if p == null:                           (0, 0)               // первый сэмпл — baseline
        elif cur.up < p.last_up || cur.down < p.last_down:
                                                (cur.up, cur.down)   // counter reset
        else:                                   (cur.up - p.last_up, cur.down - p.last_down)
    upsert traffic_state, increment traffic_daily(day) by dUp/dDown
    for client in inbound.clientStats: ... аналогично, scope_key = email ...

room.transaction {
    stateDao.upsertAll(states)
    dailyDao.incrementAll(deltas)              // up_delta += ?, down_delta += ?
    dailyDao.deleteOlderThan(day - 90 days)    // retention в той же транзакции
}
```

### scopeKey для CLIENT — email, не UUID

3x-ui отдаёт `clientStats[]` с `email`, без UUID. Соответствие email→UUID живёт внутри `inbound.settings` (JSON-строка). **Решение: scope_key = email**, плюс `inbound_id` в строке. Аргументы: `(inbound_id, email)` уникален и стабилен; парсинг settings на каждом тике — лишний CPU и точка отказа; UUID нужен только для URL-формирования (delete/update), не для агрегата. Минус: rename email отвяжет историю — описано в §9.

### Edge cases

- **Первый запуск.** `prev == null` → `delta = 0`. Реальные цифры начнутся со второго тика. UI: «собираем данные…».
- **Пропуск тиков.** Дельта за весь промежуток (часы или дни) запишется в **сегодняшний** баккет — часть трафика «протекает» в один день. Принимаем: лучше, чем терять.
- **Несколько ресетов между тиками.** Между двумя ресетами трафик потерян — `cur` отражает только последний интервал. Нерешаемо без серверной поддержки.
- **Удалили inbound/client в панели.** На след. тике он отсутствует. Запись `traffic_state` остаётся, история показывается. На retention уходит.

## 5. Retention

Одна DAO-операция `dailyDao.deleteOlderThan(cutoff)` в той же транзакции, что и запись delta. Атомарность гарантирует отсутствие window между записью и cleanup, нет отдельного worker-а. Cutoff: `utcMidnight(now) - 90 * 86_400_000`. `traffic_state` не чистим — таблица ограничена количеством текущих скоупов.

## 6. Repository layer

В `:core:data/repository/`:

```kotlin
interface TrafficHistoryRepository {
    suspend fun commitSample(panelId: String, inbounds: List<InboundDto>, sampledAt: Long): Result<Unit, DomainError>

    fun observeInboundDaily(panelId: String, inboundId: Int, fromDay: Long, toDay: Long): Flow<List<DailyPoint>>
    fun observeClientDaily(panelId: String, inboundId: Int, emailKey: String, fromDay: Long, toDay: Long): Flow<List<DailyPoint>>
}
data class DailyPoint(val dayEpoch: Long, val up: Long, val down: Long)
```

`Flow` — Room reactive query. Открытый граф «дорисовывается» сразу после нового тика без отдельного канала. Flow отдаёт точки только за дни с данными; плотный ряд (с нулями для empty days) собирает ViewModel под формат Vico.

## 7. UI integration

### `:feature:stats`

- В `InboundCard` (под существующей quota-bar) — `InboundTrafficChart(panelId, inboundId, range)`. **Внутри карточки**, не отдельной секцией ниже: контекст графика = тот же inbound; вынос вниз ломает визуальную привязку при скролле. Чарт рисуется только при `expandedIds.contains(inbound.id)` — не перегружает экран.
- Range-toggle (7/30/90) — Material 3 `SegmentedButton` под `ServerSummaryCard`, **один на весь экран**. Проще VM, проще UX. Альтернатива «per-card toggle» не оправдана для MVP.

### Drill-down per-client

- Тап по всему `ClientRow` (не отдельная иконка — строка ничего другого не делает) → новый экран `ClientStatsScreen(panelId, inboundId, emailKey)`. Route в `feature/stats/navigation/StatsNavigation.kt`: `stats/client/{panelId}/{inboundId}/{emailKey}`, emailKey URL-encoded.
- Экран: top-bar с email, 7/30/90 toggle, один большой `ClientTrafficChart`, под ним — текущие cumulative из `clientStats` (re-fetch на pull-to-refresh) для контекста.

### VM contracts

```kotlin
class StatsViewModel(...) {
    val rangeDays: StateFlow<Int>             // 7 | 30 | 90
    fun chartFlow(inboundId: Int): Flow<DailyPoints>
    fun setRange(days: Int)
}
class ClientStatsViewModel(savedState, repo, xui) {
    val uiState: StateFlow<ClientStatsUiState>
    val rangeDays: StateFlow<Int>
    fun setRange(days: Int); fun refresh()
}
```

Sampler из VM не дёргается — VM только читает.

## 8. Module placement

- **`:core:sampler`** — новый модуль (Worker, scheduler, `HiltWorkerFactory`).
- **`:core:data`** — `TrafficHistoryRepository`, DAO, entities, `AppDatabase` v3.
- **Vico** — в `:feature:stats` (используется только там). **Не** в `:core:designsystem` — он остаётся UI-toolkit без deep-domain зависимостей.
- **WorkManager** — в `:core:sampler`.

## 9. Trade-offs

- **UTC vs local для bucket.** UTC. Стабильный ключ при перелётах/DST. Минус — подпись оси сдвинута относительно локального времени; явно обозначаем «UTC» в подписи. Локальная дата ломается при смене часового пояса (31-часовой / 17-часовой день).
- **Один periodic worker, не per-panel.** Одна точка планирования, проще batch-отмена, экономнее transactions. При 1–3 панелях минусы пренебрежимы.
- **`scope_key` = email для CLIENT.** Нет лишнего парсинга settings на каждом тике. Цена — потеря истории при rename email; ROI парсинга UUID не оправдан в MVP.
- **Soft-refs (без FK на inbound/client).** Локальных таблиц нет; завести их только ради FK — over-engineering. Цена — нет каскадного удаления; компенсируется retention.
- **Sampler пишет `delta = cur` при ресете.** Альтернатива «delta = 0» теряет накопленный с ресета трафик; «delta = cur + lost» нерешаемо. Принят простейший предсказуемый вариант.

## 10. Testing strategy (JVM-only)

`reference_local_build.md`: только JVM unit, без Robolectric. Следствия:

- Pure-JVM объект **`TrafficDeltaCalculator`** (в `:core:sampler` или `:core:common`) с сигнатурой `compute(prev: Map<ScopeId, StateRow>, inbounds: List<InboundDto>, now: Long, day: Long): Sample`. Table-driven тесты: первый сэмпл, нормальный delta, ресет, исчезновение клиента, баккет = day boundary.
- **`TrafficSamplerCore`** — orchestration вынесен из `Worker` в обычный suspend-класс (`runOnce(...): Outcome`), `doWork()` — тонкий wrapper. Тесты mockk для `XuiClient`/`PanelRepository`/`TrafficHistoryRepository`.
- DAO-тесты — отложены на Connected/Robolectric (out of MVP scope). Покрытие MVP — `Calculator` + `SamplerCore` + smoke-прогон на устройстве.

## 11. Migration

- `AppDatabase.version = 3`.
- `MIGRATION_2_3` создаёт `traffic_state`, `traffic_daily` + индексы + FK на `panels`.
- `core/data/schemas/.../3.json` сгенерирует Room (schemaDirectory уже сконфигурирован).
- Существующие таблицы не меняются → миграция additive, риск нулевой.

## 12. Security review hooks (для infosec)

- WorkManager-инстанс Hilt должен использовать тот же `DbPassphraseProvider`/passphrase — иначе worker откроет «новую» БД.
- `email` клиентов — квази-PII (часто = идентификатор VPN-пользователя). В `audit_log.message` — никаких email, только counts.
- `:core:sampler` не должен тянуть `HttpLoggingInterceptor` в release.
- ProGuard/R8 rules для WorkManager и Vico — добавить в `app/proguard-rules.pro`.
- Race: одновременный pull-to-refresh в UI и worker-тик идут в одни endpoints через `XuiClient`. CookieJar один на панель — должно быть OK, но требует подтверждения.

## 13. Phasing (для тимлида)

- [ ] **Слой 1 — sampler + БД, без UI.** Завести `:core:sampler`, миграцию 2→3, DAO, `TrafficHistoryRepository`, `TrafficDeltaCalculator` + `TrafficSamplerCore` (тесты), Worker зарегистрирован в `XuiPanelApp.onCreate`. Verify: через сутки в БД видны delta-строки.
- [ ] **Слой 2 — графики per-inbound.** Vico в `:feature:stats`, `InboundTrafficChart`, range toggle, empty state.
- [ ] **Слой 3 — drill-down per-client.** `ClientStatsScreen` + ViewModel, navigation route, тап по `ClientRow`.
- [ ] **Слой 4 — retention + полировка.** `deleteOlderThan` в transaction commit, ProGuard rules, документация по фактам имплементации.

## 14. Открытые вопросы / блокеры

1. **Vico и WorkManager отсутствуют в `gradle/libs.versions.toml`**, хотя бриф и `architecture.md §11` подразумевают их «в стеке». Перед слоем 1 — добавить в version catalog (`androidx.work` ~2.9, Vico ~2.0). Это **не** нарушение CLAUDE.md §3: добавление одобрено брифом MVP-6b.
2. **`HiltWorkerFactory` setup** требует `androidx.hilt:hilt-work` + `androidx.hilt:hilt-compiler`. Подтвердить совместимость с Hilt 2.52.
3. ~~Retry на transient panel-failures.~~ Решено: `Result.retry()` если 0/N панелей удалось (глобальный сбой связи), иначе `Result.success()` (частичный успех зачитываем). Inline per-panel retry — не делаем.
